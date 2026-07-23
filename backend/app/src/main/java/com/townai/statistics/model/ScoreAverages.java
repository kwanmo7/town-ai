package com.townai.statistics.model;

/**
 * Service에서 반올림을 완료한 다섯 평가 항목 평균이다.
 *
 * <p>집계 가능한 Visit이 없으면 각 평균은 {@code null}이다.</p>
 *
 * @param atmosphere 분위기 평균
 * @param infra 생활 인프라 평균
 * @param clean 청결도 평균
 * @param size 넓은 집 가능성 평균
 * @param access 접근성 평균
 */
public record ScoreAverages(
        Double atmosphere,
        Double infra,
        Double clean,
        Double size,
        Double access
) {
}
