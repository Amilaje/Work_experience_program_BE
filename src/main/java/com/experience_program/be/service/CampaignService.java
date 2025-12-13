package com.experience_program.be.service;

import com.experience_program.be.controller.CampaignSpecification;
import com.experience_program.be.dto.*;
import com.experience_program.be.entity.*;
import com.experience_program.be.repository.CampaignRepository;
import com.experience_program.be.repository.ChatMessageRepository;
import com.experience_program.be.repository.ChatSessionRepository;
import com.experience_program.be.repository.MessageResultRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final MessageResultRepository messageResultRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final WebClient webClient;
    private final ObjectMapper objectMapper;

    private static final int MAX_CHAT_SESSIONS = 50;

    @Autowired
    public CampaignService(CampaignRepository campaignRepository, MessageResultRepository messageResultRepository,
                           ChatSessionRepository chatSessionRepository, ChatMessageRepository chatMessageRepository,
                           WebClient webClient, ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.messageResultRepository = messageResultRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.webClient = webClient;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CampaignChatResponseDto handleInteractiveBuild(CampaignChatRequestDto request) {
        // 1. AI 서버 호출을 동기 방식으로 변경
        CampaignChatResponseDto response = webClient.post()
                .uri("/api/build-campaign/interactive")
                .body(Mono.just(request), CampaignChatRequestDto.class)
                .retrieve()
                .bodyToMono(CampaignChatResponseDto.class)
                .block(); // 응답이 올 때까지 대기

        if (response != null) {
            // 2. DB 저장 로직을 동기 코드 블록 안에서 실행
            saveChatHistory(request, response);
            enforceChatSessionLimit();
        }

        return response;
    }

    private void saveChatHistory(CampaignChatRequestDto request, CampaignChatResponseDto response) {
        String conversationId = response.getConversationId();
        ChatSession session = chatSessionRepository.findById(conversationId)
                .orElseGet(() -> ChatSession.builder().conversationId(conversationId).build());

        // AI가 생성한 캠페인 제목으로 세션 제목 업데이트
        if (response.getCurrentCampaignData() != null && StringUtils.hasText(response.getCurrentCampaignData().getCampaignTitle())) {
            session.setTitle(response.getCurrentCampaignData().getCampaignTitle());
        }

        // 사용자 메시지 저장
        ChatMessage userMessage = ChatMessage.builder()
                .session(session)
                .role("user")
                .content(request.getUserMessage())
                .build();
        session.getMessages().add(userMessage);

        // AI 응답 메시지 저장
        ChatMessage aiMessage = ChatMessage.builder()
                .session(session)
                .role("assistant")
                .content(response.getAiResponse())
                .build();
        session.getMessages().add(aiMessage);

        chatSessionRepository.save(session);
    }

    private void enforceChatSessionLimit() {
        long totalSessions = chatSessionRepository.count();
        if (totalSessions > MAX_CHAT_SESSIONS) {
            chatSessionRepository.findFirstByOrderByLastUpdatedAtAsc()
                    .ifPresent(chatSessionRepository::delete);
        }
    }

    // ... (기존의 다른 메서드들은 그대로 유지)
    @Transactional
    public Campaign createCampaign(CampaignRequestDto campaignRequestDto) {
        String sourceUrlsJson = convertObjectToJson(campaignRequestDto.getSourceUrls());
        String customColumnsJson = convertObjectToJson(campaignRequestDto.getCustomColumns());

        Campaign campaign = Campaign.builder()
                .marketerId(campaignRequestDto.getMarketerId())
                .purpose(campaignRequestDto.getPurpose())
                .coreBenefitText(campaignRequestDto.getCoreBenefitText())
                .sourceUrl(sourceUrlsJson)
                .customColumns(customColumnsJson)
                .status("PROCESSING")
                .requestDate(LocalDateTime.now())
                .performanceStatus(PerformanceStatus.UNDECIDED)
                .isPerformanceRegistered(false)
                .isRagRegistered(false)
                .build();
        Campaign savedCampaign = campaignRepository.save(campaign);

        webClient.post()
                .uri("/api/generate")
                .body(Mono.just(campaignRequestDto), CampaignRequestDto.class)
                .retrieve()
                .bodyToMono(AiResponseDto.class)
                .doOnError(error -> updateCampaignStatus(savedCampaign.getCampaignId(), "FAILED"))
                .subscribe(aiResponse -> {
                    saveAiResponse(savedCampaign, aiResponse);
                    updateCampaignStatus(savedCampaign.getCampaignId(), "COMPLETED");
                });

        return savedCampaign;
    }

    @Transactional
    public void saveAiResponse(Campaign campaign, AiResponseDto aiResponse) {
        List<MessageResult> messageResults = aiResponse.getTarget_groups().stream()
                .flatMap(targetGroupDto -> targetGroupDto.getMessage_drafts().stream()
                        .map(messageDraftDto -> {
                            String validationReportJson = convertObjectToJson(messageDraftDto.getValidationReport());
                            return MessageResult.builder()
                                    .campaign(campaign)
                                    .targetGroupIndex(targetGroupDto.getTarget_group_index())
                                    .targetName(targetGroupDto.getTarget_name())
                                    .targetFeatures(targetGroupDto.getTarget_features())
                                    .classificationReason(targetGroupDto.getClassification_reason())
                                    .messageDraftIndex(messageDraftDto.getMessageDraftIndex())
                                    .messageText(messageDraftDto.getMessageText())
                                    .validatorReport(validationReportJson)
                                    .isSelected(false)
                                    .build();
                        }))
                .collect(Collectors.toList());
        messageResultRepository.saveAll(messageResults);
    }

    public Page<Campaign> getAllCampaigns(LocalDate requestDate, String status, String purpose, String marketerId, Pageable pageable) {
        Specification<Campaign> spec = CampaignSpecification.withDynamicQuery(requestDate, status, purpose, marketerId);
        return campaignRepository.findAll(spec, pageable);
    }

    public Campaign getCampaignById(UUID campaignId) {
        return campaignRepository.findById(campaignId)
                .orElseThrow(() -> new ResourceNotFoundException("ID " + campaignId + "에 해당하는 캠페인을 찾을 수 없습니다."));
    }

    @Transactional
    public void selectMessage(UUID campaignId, List<UUID> resultIds) {
        Campaign campaign = getCampaignById(campaignId);
        List<MessageResult> allResults = messageResultRepository.findByCampaign_CampaignId(campaignId);
        allResults.forEach(result -> result.setSelected(false));

        List<MessageResult> selectedResults = messageResultRepository.findAllById(resultIds);
        for (MessageResult result : selectedResults) {
            if (!result.getCampaign().getCampaignId().equals(campaignId)) {
                throw new IllegalArgumentException("선택된 메시지(ID: " + result.getResultId() + ")가 현재 캠페인에 속해있지 않습니다.");
            }
            result.setSelected(true);
        }
        
        messageResultRepository.saveAll(allResults);
        messageResultRepository.saveAll(selectedResults);
        updateCampaignStatus(campaignId, "MESSAGE_SELECTED");
    }

    @Transactional
    public void refineMessage(UUID campaignId, String feedback) {
        Campaign campaign = getCampaignById(campaignId);
        updateCampaignStatus(campaignId, "REFINING");

        CampaignRequestDto campaignContext = new CampaignRequestDto();
        campaignContext.setMarketerId(campaign.getMarketerId());
        campaignContext.setPurpose(campaign.getPurpose());
        campaignContext.setCoreBenefitText(campaign.getCoreBenefitText());
        campaignContext.setSourceUrls(convertJsonToList(campaign.getSourceUrl()));
        campaignContext.setCustomColumns(convertJsonToMap(campaign.getCustomColumns()));

        List<MessageResult> previousResults = messageResultRepository.findByCampaign_CampaignId(campaignId);
        List<Map<String, Object>> targetPersonas = previousResults.stream()
                .map(result -> {
                    Map<String, Object> persona = new HashMap<>();
                    persona.put("target_group_index", result.getTargetGroupIndex());
                    persona.put("target_name", result.getTargetName());
                    persona.put("target_features", result.getTargetFeatures());
                    return persona;
                })
                .distinct()
                .collect(Collectors.toList());

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("campaign_context", campaignContext);
        requestBody.put("feedback_text", feedback);
        requestBody.put("target_personas", targetPersonas);

        webClient.post()
                .uri("/api/campaigns/" + campaignId + "/refine")
                .body(Mono.just(requestBody), Map.class)
                .retrieve()
                .bodyToMono(AiResponseDto.class)
                .doOnError(error -> {
                    System.err.println("Error during refine call: " + error.getMessage());
                    updateCampaignStatus(campaign.getCampaignId(), "FAILED");
                })
                .subscribe(aiResponse -> {
                    List<MessageResult> oldResults = messageResultRepository.findByCampaign_CampaignId(campaignId);
                    messageResultRepository.deleteAll(oldResults);
                    saveAiResponse(campaign, aiResponse);
                    updateCampaignStatus(campaign.getCampaignId(), "COMPLETED");
                });
    }

    @Transactional
    public void deleteCampaign(UUID campaignId) {
        if (!campaignRepository.existsById(campaignId)) {
            throw new ResourceNotFoundException("ID " + campaignId + "에 해당하는 캠페인을 찾을 수 없습니다.");
        }
        campaignRepository.deleteById(campaignId);
    }

    @Transactional
    public void updatePerformance(UUID campaignId, CampaignPerformanceUpdateDto performanceDto) {
        Campaign campaign = getCampaignById(campaignId);
        campaign.setActualCtr(performanceDto.getActualCtr());
        campaign.setConversionRate(performanceDto.getConversionRate());
        campaign.setPerformanceStatus(performanceDto.getPerformanceStatus());
        campaign.setPerformanceNotes(performanceDto.getPerformanceNotes());
        campaign.setPerformanceRegistered(true);

        switch (performanceDto.getPerformanceStatus()) {
            case SUCCESS:
                campaign.setStatus("SUCCESS_CASE");
                break;
            case FAILURE:
            case UNDECIDED:
            default:
                campaign.setStatus("PERFORMANCE_REGISTERED");
                break;
        }
        
        campaignRepository.save(campaign);
    }

    @Transactional
    public void triggerRagRegistration(UUID campaignId) {
        Campaign campaign = getCampaignById(campaignId);

        if (!campaign.isPerformanceRegistered()) {
            throw new IllegalStateException("성과가 등록되지 않은 캠페인은 RAG DB에 등록할 수 없습니다.");
        }
        if (campaign.getPerformanceStatus() == PerformanceStatus.UNDECIDED) {
            throw new IllegalStateException("'미정' 상태의 캠페인은 RAG DB에 등록할 수 없습니다.");
        }

        List<MessageResult> selectedMessages = messageResultRepository.findByCampaign_CampaignIdAndIsSelected(campaign.getCampaignId(), true);
        if (selectedMessages.isEmpty()) {
            throw new IllegalStateException("RAG DB에 등록할 최종 선택된 메시지가 없습니다.");
        }

        String title;
        String sourceType = (campaign.getPerformanceStatus() == PerformanceStatus.SUCCESS) ? "성공_사례" : "실패_사례";
        title = sourceType.replace("_", " ") + ": " + campaign.getPurpose();

        String combinedMessages = IntStream.range(0, selectedMessages.size())
                .mapToObj(i -> String.format("[메시지 %d] 타겟: %s\n내용: %s",
                        i + 1,
                        selectedMessages.get(i).getTargetName(),
                        selectedMessages.get(i).getMessageText()))
                .collect(Collectors.joining("\n\n---\n\n"));

        String performanceSection = String.format("--- 성과 ---\nCTR: %s\n전환율: %s",
                campaign.getActualCtr(), campaign.getConversionRate());
        if (StringUtils.hasText(campaign.getPerformanceNotes())) {
            performanceSection += "\n\n--- 성과 분석 ---\n" + campaign.getPerformanceNotes();
        }

        String content = String.format(
                "캠페인 목적: %s\n핵심 혜택: %s\n\n--- 참고 메시지 ---\n\n%s\n\n%s",
                campaign.getPurpose(),
                campaign.getCoreBenefitText(),
                combinedMessages,
                performanceSection
        );

        SuccessCaseDto successCaseDto = new SuccessCaseDto(
                title,
                content,
                sourceType,
                campaign.getCampaignId().toString(),
                campaign.getRequestDate()
        );

        webClient.post()
                .uri("/api/knowledge")
                .body(Mono.just(successCaseDto), SuccessCaseDto.class)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(aVoid -> {
                    campaign.setRagRegistered(true);
                    campaign.setStatus("RAG_REGISTERED");
                    campaignRepository.save(campaign);
                })
                .subscribe();
    }

    @Transactional
    public void updateCampaignStatus(UUID campaignId, String newStatus) {
        Campaign campaign = getCampaignById(campaignId);
        campaign.setStatus(newStatus);
        campaignRepository.save(campaign);
    }

    // Helper methods for JSON conversion
    private String convertObjectToJson(Object object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("객체를 JSON으로 변환하는 데 실패했습니다.", e);
        }
    }

    private List<String> convertJsonToList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (IOException e) {
            throw new RuntimeException("JSON을 리스트로 변환하는 데 실패했습니다.", e);
        }
    }

    private Map<String, Object> convertJsonToMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            throw new RuntimeException("JSON을 맵으로 변환하는 데 실패했습니다.", e);
        }
    }
}
