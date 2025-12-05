package com.experience_program.be.service;

import com.experience_program.be.dto.AiKnowledgeDto;
import com.experience_program.be.dto.KnowledgeRequestDto;
import com.experience_program.be.dto.KnowledgeUpdateDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class KnowledgeService {

    private final WebClient webClient;

    @Autowired
    public KnowledgeService(WebClient webClient) {
        this.webClient = webClient;
    }

    public void registerKnowledge(KnowledgeRequestDto requestDto) {
        String newCampaignId = "knowledge-" + UUID.randomUUID().toString();
        LocalDateTime registrationDateTime = LocalDateTime.now();

        AiKnowledgeDto knowledgeData = new AiKnowledgeDto(
                newCampaignId,
                requestDto.getCampaignSummary(),
                requestDto.getCampaignDetails(),
                registrationDateTime
        );

        webClient.post()
                .uri("/api/knowledge")
                .body(Mono.just(knowledgeData), AiKnowledgeDto.class)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> {
                    System.err.println("Error while registering knowledge: " + error.getMessage());
                })
                .subscribe();
    }

    public Mono<Object> getAllKnowledge(String title, String sourceType, Pageable pageable) {
        return webClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/knowledge")
                            .queryParam("page", pageable.getPageNumber())
                            .queryParam("size", pageable.getPageSize());

                    if (StringUtils.hasText(title)) {
                        uriBuilder.queryParam("title__contains", title);
                    }
                    if (StringUtils.hasText(sourceType)) {
                        uriBuilder.queryParam("source_type", sourceType);
                    }

                    if (pageable.getSort().isSorted()) {
                        String sortString = pageable.getSort().stream()
                                .map(order -> order.getProperty() + "," + order.getDirection().name().toLowerCase())
                                .collect(Collectors.joining());
                        uriBuilder.queryParam("sort", sortString);
                    }
                    return uriBuilder.build();
                })
                .retrieve()
                .bodyToMono(Object.class);
    }

    public Mono<Object> getKnowledgeById(String knowledgeId) {
        return webClient.get()
                .uri("/api/knowledge/" + knowledgeId)
                .retrieve()
                .bodyToMono(Object.class);
    }

    public void updateKnowledge(String knowledgeId, KnowledgeUpdateDto requestDto) {
        Map<String, Object> metadata = new HashMap<>(requestDto.getCampaignDetails());
        metadata.put("campaign_id", knowledgeId);
        metadata.put("status", "successful");
        metadata.put("updated_at", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("document", requestDto.getCampaignSummary());
        requestBody.put("metadata", metadata);

        webClient.put()
                .uri("/api/knowledge/" + knowledgeId)
                .body(Mono.just(requestBody), Map.class)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> {
                    System.err.println("Error while updating knowledge: " + error.getMessage());
                })
                .subscribe();
    }

    public void deleteKnowledge(String knowledgeId) {
        webClient.delete()
                .uri("/api/knowledge/" + knowledgeId)
                .retrieve()
                .bodyToMono(Void.class)
                .doOnError(error -> {
                    System.err.println("Error while deleting knowledge: " + error.getMessage());
                })
                .subscribe();
    }
}
