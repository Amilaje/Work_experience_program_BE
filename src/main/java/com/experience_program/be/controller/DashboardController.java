package com.experience_program.be.controller;

import com.experience_program.be.dto.MonthlyStatusCountDto;
import com.experience_program.be.entity.Campaign;
import com.experience_program.be.service.DashboardService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    @Autowired
    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    // 월별 캠페인 상태(진행중/완료) 수 조회 (최근 6개월)
    @GetMapping("/summary")
    public ResponseEntity<List<MonthlyStatusCountDto>> getMonthlyCampaignSummary() {
        List<MonthlyStatusCountDto> summary = dashboardService.getMonthlyCampaignSummary();
        return ResponseEntity.ok(summary);
    }

    // 최근 활동 조회
    @GetMapping("/recent-activity")
    public ResponseEntity<List<Campaign>> getRecentActivity() {
        List<Campaign> recentCampaigns = dashboardService.getRecentActivity();
        return ResponseEntity.ok(recentCampaigns);
    }
}
