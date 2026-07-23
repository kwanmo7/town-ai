package com.townai.statistics.model;

/**
 * 특정 활성 Area의 통계 Use Case가 반환하는 API 독립적인 결과이다.
 *
 * @param areaId Area 식별자
 * @param areaName 사용자에게 표시할 Area 이름
 * @param visitCount 집계에 포함된 Visit 수
 * @param averageScores 해당 Area의 Visit별 항목 평균
 */
public record AreaStatistics(
        Long areaId,
        String areaName,
        long visitCount,
        ScoreAverages averageScores
) {
}
