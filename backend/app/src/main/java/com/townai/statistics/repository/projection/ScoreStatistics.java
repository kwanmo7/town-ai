package com.townai.statistics.repository.projection;

/**
 * Visit 수와 다섯 평가 항목의 원시 평균을 DB에서 집계한 Projection이다.
 *
 * @param visitCount 집계 조건에 포함된 Visit 수
 * @param atmosphere 반올림 전 분위기 평균
 * @param infra 반올림 전 생활 인프라 평균
 * @param clean 반올림 전 청결도 평균
 * @param size 반올림 전 넓은 집 가능성 평균
 * @param access 반올림 전 접근성 평균
 */
public record ScoreStatistics(
        Long visitCount,
        Double atmosphere,
        Double infra,
        Double clean,
        Double size,
        Double access
) {
}
