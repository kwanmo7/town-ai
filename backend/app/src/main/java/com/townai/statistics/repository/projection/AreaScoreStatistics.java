package com.townai.statistics.repository.projection;

/**
 * Statistics Top 5 계산에 사용하는 Area별 원시 평균 Projection이다.
 *
 * @param areaId Area 식별자
 * @param areaName Area 이름
 * @param atmosphere 반올림 전 분위기 평균
 * @param infra 반올림 전 생활 인프라 평균
 * @param clean 반올림 전 청결도 평균
 * @param size 반올림 전 넓은 집 가능성 평균
 * @param access 반올림 전 접근성 평균
 */
public record AreaScoreStatistics(
        Long areaId,
        String areaName,
        Double atmosphere,
        Double infra,
        Double clean,
        Double size,
        Double access
) {
}
