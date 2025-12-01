package com.experience_program.be.service;

import com.experience_program.be.controller.CampaignSpecification;
import com.experience_program.be.dto.*;
import com.experience_program.be.entity.Campaign;
import com.experience_program.be.entity.MessageResult;
import com.experience_program.be.repository.CampaignRepository;
import com.experience_program.be.repository.MessageResultRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    private final WebClient webClient;

    @Autowired
    public CampaignService(CampaignRepository campaignRepository, MessageResultRepository messageResultRepository, WebClient webClient) {
        this.campaignRepository = campaignRepository;
        this.messageResultRepository = messageResultRepository;
        this.webClient = webClient;
    }

    @Transactional
    public Campaign createCampaign(CampaignRequestDto campaignRequestDto) {
        Campaign campaign = Campaign.builder()
                .marketerId(campaignRequestDto.getMarketerId())
                .purpose(campaignRequestDto.getPurpose())
                .coreBenefitText(campaignRequestDto.getCoreBenefitText())
                .sourceUrl(campaignRequestDto.getSourceUrl())
                .customColumns(campaignRequestDto.getCustomColumns())
                .status("PROCESSING")
                .requestDate(LocalDateTime.now())
                .isSuccessCase(false)
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
                        .map(messageDraftDto -> MessageResult.builder()
                                .campaign(campaign)
                                .targetGroupIndex(targetGroupDto.getTarget_group_index())
                                .targetName(targetGroupDto.getTarget_name())
                                .targetFeatures(targetGroupDto.getTarget_features())
                                .messageDraftIndex(messageDraftDto.getMessage_draft_index())
                                .messageText(messageDraftDto.getMessage_text())
                                .validatorReport(messageDraftDto.getValidator_report())
                                .isSelected(false)
                                .build()))
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
        // 1. Verify campaign exists
        Campaign campaign = getCampaignById(campaignId);

        // 2. Reset all messages for this campaign to isSelected = false
        List<MessageResult> allResults = messageResultRepository.findByCampaign_CampaignId(campaignId);
        allResults.forEach(result -> result.setSelected(false));

        // 3. Set isSelected = true for the provided resultIds
        List<MessageResult> selectedResults = messageResultRepository.findAllById(resultIds);
        for (MessageResult result : selectedResults) {
            if (!result.getCampaign().getCampaignId().equals(campaignId)) {
                throw new IllegalArgumentException("선택된 메시지(ID: " + result.getResultId() + ")가 현재 캠페인에 속해있지 않습니다.");
            }
            result.setSelected(true);
        }
        
        messageResultRepository.saveAll(allResults);
        messageResultRepository.saveAll(selectedResults);

        // 4. Update campaign status
        updateCampaignStatus(campaignId, "MESSAGE_SELECTED");
    }

    @Transactional
    public void refineMessage(UUID campaignId, String feedback) {
        Campaign campaign = getCampaignById(campaignId);
        updateCampaignStatus(campaignId, "REFINING");

        // 1. Reconstruct campaign_context
        CampaignRequestDto campaignContext = new CampaignRequestDto();
        campaignContext.setMarketerId(campaign.getMarketerId());
        campaignContext.setPurpose(campaign.getPurpose());
        campaignContext.setCoreBenefitText(campaign.getCoreBenefitText());
        campaignContext.setSourceUrl(campaign.getSourceUrl());
        campaignContext.setCustomColumns(campaign.getCustomColumns());

        // 2. Reconstruct target_personas from previous results
        List<MessageResult> previousResults = messageResultRepository.findByCampaign_CampaignId(campaignId);
        List<Map<String, Object>> targetPersonas = previousResults.stream()
                .map(result -> {
                    Map<String, Object> persona = new HashMap<>();
                    persona.put("target_group_index", result.getTargetGroupIndex());
                    persona.put("target_name", result.getTargetName());
                    persona.put("target_features", result.getTargetFeatures());
                    return persona;
                })
                .distinct() // 중복된 페르소나 정보 제거
                .collect(Collectors.toList());

        // 3. Assemble the final request body for AI server
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("campaign_context", campaignContext);
        requestBody.put("feedback_text", feedback);
        requestBody.put("target_personas", targetPersonas); // 페르소나 정보 추가

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
        campaign.setSuccessCase(performanceDto.getIsSuccessCase());
        campaign.setPerformanceRegistered(true);

        // status 업데이트 로직 추가
        if (performanceDto.getIsSuccessCase()) {
            campaign.setStatus("SUCCESS_CASE");
        } else {
            campaign.setStatus("PERFORMANCE_REGISTERED");
        }
        
        campaignRepository.save(campaign);
    }

    @Transactional
    public void triggerRagRegistration(UUID campaignId) {
        Campaign campaign = getCampaignById(campaignId);

        if (!campaign.isPerformanceRegistered()) {
            throw new IllegalStateException("성과가 등록되지 않은 캠페인은 RAG DB에 등록할 수 없습니다.");
        }

        List<MessageResult> selectedMessages = messageResultRepository.findByCampaign_CampaignIdAndIsSelected(campaign.getCampaignId(), true);
        if (selectedMessages.isEmpty()) {
            throw new IllegalStateException("RAG DB에 등록할 최종 선택된 메시지가 없습니다.");
        }

        String title;
        String sourceType;
        String content;

        // 선택된 모든 메시지를 하나의 문자열로 조합
        String combinedMessages = IntStream.range(0, selectedMessages.size())
                .mapToObj(i -> String.format("메시지 %d: %s", i + 1, selectedMessages.get(i).getMessageText()))
                .collect(Collectors.joining("\n"));

        if (campaign.isSuccessCase()) {
            title = "성공사례: " + campaign.getPurpose();
            sourceType = "성공_사례";
            content = String.format(
                    "캠페인 목적: %s\n핵심 혜택: %s\n%s",
                    campaign.getPurpose(),
                    campaign.getCoreBenefitText(),
                    combinedMessages
            );
        } else {
            title = "실패사례: " + campaign.getPurpose();
            sourceType = "실패_사례";
            content = String.format(
                    "캠페인 목적: %s\n핵심 혜택: %s\n%s\n(CTR: %s, 전환율: %s)",
                    campaign.getPurpose(),
                    campaign.getCoreBenefitText(),
                    combinedMessages,
                    campaign.getActualCtr(),
                    campaign.getConversionRate()
            );
        }

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
}
