package com.townai.statistics.dto;

import com.townai.statistics.model.ScoreAverages;

/**
 * 다섯 평가 항목의 소수점 첫째 자리 평균을 표현하는 API 응답이다.
 *
 * @param atmosphere 분위기 평균
 * @param infra 생활 인프라 평균
 * @param clean 청결도 평균
 * @param size 넓은 집 가능성 평균
 * @param access 접근성 평균
 */
public record ScoreAveragesResponse(
        Double atmosphere,
        Double infra,
        Double clean,
        Double size,
        Double access
) {

    /**
     * 내부 평균 모델을 API 응답으로 변환한다.
     *
     * @param averages Service의 내부 평균 모델
     * @return API 평균 응답
     */
    public static ScoreAveragesResponse from(ScoreAverages averages) {
        return new ScoreAveragesResponse(
                averages.atmosphere(),
                averages.infra(),
                averages.clean(),
                averages.size(),
                averages.access()
        );
    }
}
