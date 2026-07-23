package com.townai.statistics.model;

/**
 * 전체 통계 Use Case가 반환하는 API 독립적인 결과이다.
 *
 * @param areaCount 활성 Area 수
 * @param visitCount 활성 Area에 속한 Visit 수
 * @param averageScores 모든 Visit을 같은 비중으로 계산한 항목별 평균
 * @param top5 Area별 평균을 기준으로 계산한 항목별 Top 5
 */
public record OverallStatistics(
        long areaCount,
        long visitCount,
        ScoreAverages averageScores,
        TopFive top5
) {
}
