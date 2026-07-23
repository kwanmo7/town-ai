package com.townai.statistics.dto;

import com.townai.statistics.model.OverallStatistics;

/**
 * 삭제되지 않은 모든 Area와 그 Visit을 대상으로 계산한 전체 통계 응답이다.
 *
 * @param areaCount 활성 Area 수
 * @param visitCount 집계에 포함된 Visit 수
 * @param averageScores 모든 Visit의 항목별 평균
 * @param top5 Area별 평균을 기준으로 한 항목별 상위 목록
 */
public record StatisticsResponse(
        long areaCount,
        long visitCount,
        ScoreAveragesResponse averageScores,
        TopFiveResponse top5
) {

    /**
     * 내부 전체 통계를 API 응답으로 변환한다.
     *
     * @param statistics Service가 계산한 내부 전체 통계
     * @return HTTP 응답에 사용할 전체 통계 DTO
     */
    public static StatisticsResponse from(OverallStatistics statistics) {
        return new StatisticsResponse(
                statistics.areaCount(),
                statistics.visitCount(),
                ScoreAveragesResponse.from(statistics.averageScores()),
                TopFiveResponse.from(statistics.top5())
        );
    }
}
