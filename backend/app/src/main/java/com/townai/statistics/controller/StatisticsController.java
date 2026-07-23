package com.townai.statistics.controller;

import com.townai.statistics.dto.AreaStatisticsResponse;
import com.townai.statistics.dto.StatisticsResponse;
import com.townai.statistics.service.StatisticsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전체 및 Area별 Visit 통계를 읽기 전용 REST API로 노출한다.
 *
 * <p>Service의 내부 통계 모델은 Controller에서 HTTP 응답 DTO로 변환해,
 * Report 생성 로직이 API 표현 형식에 의존하지 않도록 한다.</p>
 */
@RestController
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * Statistics Controller를 생성한다.
     *
     * @param statisticsService 전체 및 Area별 통계를 계산할 Service
     */
    public StatisticsController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * 활성 Area와 그 Visit을 대상으로 전체 평균과 항목별 Top 5를 조회한다.
     *
     * @return 전체 통계 API 응답
     */
    @GetMapping("/api/statistics")
    public StatisticsResponse getOverallStatistics() {
        return StatisticsResponse.from(
                statisticsService.getOverallStatistics()
        );
    }

    /**
     * 활성 Area 한 곳의 Visit 수와 평균 점수를 조회한다.
     *
     * @param areaId 조회할 활성 Area ID
     * @return Area별 통계 API 응답
     */
    @GetMapping("/api/areas/{areaId}/statistics")
    public AreaStatisticsResponse getAreaStatistics(
            @PathVariable Long areaId
    ) {
        return AreaStatisticsResponse.from(
                statisticsService.getAreaStatistics(areaId)
        );
    }
}
