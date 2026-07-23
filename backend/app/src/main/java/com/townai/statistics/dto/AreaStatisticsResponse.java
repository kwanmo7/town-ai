package com.townai.statistics.dto;

import com.townai.statistics.model.AreaStatistics;

/**
 * 삭제되지 않은 특정 Area의 Visit 통계 응답이다.
 *
 * @param areaId Area 식별자
 * @param areaName 사용자에게 표시할 Area 이름
 * @param visitCount 집계에 포함된 Visit 수
 * @param averageScores Visit별 항목 점수의 평균
 */
public record AreaStatisticsResponse(
        Long areaId,
        String areaName,
        long visitCount,
        ScoreAveragesResponse averageScores
) {

    /**
     * 내부 Area 통계를 API 응답으로 변환한다.
     *
     * @param statistics Service가 계산한 내부 Area 통계
     * @return HTTP 응답에 사용할 Area 통계 DTO
     */
    public static AreaStatisticsResponse from(AreaStatistics statistics) {
        return new AreaStatisticsResponse(
                statistics.areaId(),
                statistics.areaName(),
                statistics.visitCount(),
                ScoreAveragesResponse.from(statistics.averageScores())
        );
    }
}
