package com.experience_program.be.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class MonthlyCampaignCountDto {
    private String month;
    private long count;
}
