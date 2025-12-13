package com.experience_program.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ConversationEntryDto {
    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;
}
