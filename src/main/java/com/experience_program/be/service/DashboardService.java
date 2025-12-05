package com.experience_program.be.service;

import com.experience_program.be.dto.MonthlyStatusCountDto;
import com.experience_program.be.entity.Campaign;
import com.experience_program.be.repository.CampaignRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DashboardService {

    private final CampaignRepository campaignRepository;

    @Autowired
    public DashboardService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public List<MonthlyStatusCountDto> getMonthlyCampaignSummary() {
        // 1. 상태 분류 정의
        List<String> ongoingStatuses = Arrays.asList("PROCESSING", "REFINING", "COMPLETED", "MESSAGE_SELECTED");
        List<String> completedStatuses = Arrays.asList("PERFORMANCE_REGISTERED", "SUCCESS_CASE", "RAG_REGISTERED");

        // 2. 기준 날짜 설정 (오늘로부터 6개월 전)
        LocalDateTime startDate = LocalDate.now().minusMonths(6).withDayOfMonth(1).atStartOfDay();

        // 3. 최근 6개월의 모든 월을 0으로 초기화 (순서 보장을 위해 LinkedHashMap 사용)
        Map<String, MonthlyStatusCountDto> monthlyMap = new LinkedHashMap<>();
        YearMonth currentMonth = YearMonth.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
        IntStream.range(0, 6)
                .mapToObj(currentMonth::minusMonths)
                .sorted()
                .forEach(month -> monthlyMap.put(month.format(formatter), new MonthlyStatusCountDto(month.format(formatter))));

        // 4. DB에서 월별/상태별 캠페인 수 조회
        List<Object[]> dbResults = campaignRepository.countMonthlyCampaignsByStatusSince(startDate);

        // 5. DB 결과를 순회하며 맵에 값 채우기
        for (Object[] row : dbResults) {
            String month = (String) row[0];
            String status = (String) row[1];
            long count = (Long) row[2];

            MonthlyStatusCountDto dto = monthlyMap.get(month);
            if (dto != null) {
                if (ongoingStatuses.contains(status)) {
                    dto.setOngoingCount(dto.getOngoingCount() + count);
                } else if (completedStatuses.contains(status)) {
                    dto.setCompletedCount(dto.getCompletedCount() + count);
                }
            }
        }

        // 6. 맵의 값들을 리스트로 변환하여 반환
        return new ArrayList<>(monthlyMap.values());
    }

    public List<Campaign> getRecentActivity() {
        return campaignRepository.findTop5ByOrderByRequestDateDesc();
    }
}
