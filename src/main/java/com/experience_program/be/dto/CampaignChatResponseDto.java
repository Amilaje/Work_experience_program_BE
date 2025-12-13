package com.experience_program.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CampaignChatResponseDto {
    @JsonProperty("ai_response")
    private String aiResponse;

    @JsonProperty("conversation_history")
    private List<ConversationEntryDto> conversationHistory;

    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("current_campaign_data")
    private CurrentCampaignDataDto currentCampaignData;

    @JsonProperty("is_finished")
    private boolean isFinished;
}
