package com.experience_program.be.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class CurrentCampaignDataDto {
    @JsonProperty("campaignTitle")
    private String campaignTitle;

    @JsonProperty("coreBenefitText")
    private String coreBenefitText;

    @JsonProperty("customColumns")
    private Map<String, Object> customColumns;

    @JsonProperty("sourceUrls")
    private List<String> sourceUrls;
}
