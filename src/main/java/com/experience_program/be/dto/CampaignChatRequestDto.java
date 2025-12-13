package com.experience_program.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CampaignChatRequestDto {
    @JsonProperty("conversation_id")
    private String conversationId;

    @JsonProperty("user_message")
    private String userMessage;
}
