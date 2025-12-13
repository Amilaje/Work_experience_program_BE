package com.experience_program.be.controller;

import com.experience_program.be.dto.*;
import com.experience_program.be.entity.Campaign;
import com.experience_program.be.service.CampaignService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/campaigns" )
public class CampaignController {

    private final CampaignService campaignService;

    @Autowired
    public CampaignController(CampaignService campaignService) {
        this.campaignService = campaignService;
    }

    @PostMapping("/build/interactive")
    public ResponseEntity<CampaignChatResponseDto> handleInteractiveBuild(@RequestBody CampaignChatRequestDto request) {
        CampaignChatResponseDto response = campaignService.handleInteractiveBuild(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<Campaign> createCampaign(@Valid @RequestBody CampaignRequestDto campaignRequest) {
        Campaign createdCampaign = campaignService.createCampaign(campaignRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdCampaign);
    }

    @GetMapping
    public ResponseEntity<Page<Campaign>> getAllCampaigns(
            @RequestParam(required = false) LocalDate requestDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String purpose,
            @RequestParam(required = false) String marketerId,
            Pageable pageable
    ) {
        Page<Campaign> campaigns = campaignService.getAllCampaigns(requestDate, status, purpose, marketerId, pageable);
        return ResponseEntity.ok(campaigns);
    }

    @GetMapping("/{campaign_id}")
    public ResponseEntity<Campaign> getCampaignById(@PathVariable("campaign_id") UUID campaignId) {
        Campaign campaign = campaignService.getCampaignById(campaignId);
        return ResponseEntity.ok(campaign);
    }

    @PutMapping("/{campaign_id}/selection")
    public ResponseEntity<Void> selectMessage(@PathVariable("campaign_id") UUID campaignId, @RequestBody MessageSelectionDto selectionDto) {
        campaignService.selectMessage(campaignId, selectionDto.getResultIds());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaign_id}/refine")
    public ResponseEntity<Void> refineMessage(@PathVariable("campaign_id") UUID campaignId, @RequestBody RefineRequestDto refineRequest) {
        campaignService.refineMessage(campaignId, refineRequest.getFeedbackText());
        return ResponseEntity.accepted().build(); // Accepted: 요청이 접수되었으나 처리는 비동기
    }

    @DeleteMapping("/{campaign_id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteCampaign(@PathVariable("campaign_id") UUID campaignId) {
        campaignService.deleteCampaign(campaignId);
    }

    @PutMapping("/{campaign_id}/performance")
    public ResponseEntity<Void> updatePerformance(@PathVariable("campaign_id") UUID campaignId, @Valid @RequestBody CampaignPerformanceUpdateDto performanceRequest) {
        campaignService.updatePerformance(campaignId, performanceRequest);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{campaign_id}/rag-trigger")
    public ResponseEntity<Void> triggerRagRegistration(@PathVariable("campaign_id") UUID campaignId) {
        campaignService.triggerRagRegistration(campaignId);
        return ResponseEntity.accepted().build(); // Accepted: 요청이 접수되었으나 처리는 비동기
    }
}
