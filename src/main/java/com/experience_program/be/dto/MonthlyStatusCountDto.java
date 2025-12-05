package com.experience_program.be.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MonthlyStatusCountDto {
    private String month;
    private long ongoingCount;
    private long completedCount;

    public MonthlyStatusCountDto(String month) {
        this.month = month;
        this.ongoingCount = 0;
        this.completedCount = 0;
    }
}
