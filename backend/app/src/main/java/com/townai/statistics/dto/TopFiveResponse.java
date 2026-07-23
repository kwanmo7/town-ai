package com.townai.statistics.dto;

import com.townai.statistics.model.TopArea;
import com.townai.statistics.model.TopFive;

import java.util.List;

/**
 * 다섯 평가 항목의 Area별 평균 Top 5 API 응답이다.
 *
 * @param atmosphere 분위기 평균 상위 Area
 * @param infra 생활 인프라 평균 상위 Area
 * @param clean 청결도 평균 상위 Area
 * @param size 넓은 집 가능성 평균 상위 Area
 * @param access 접근성 평균 상위 Area
 */
public record TopFiveResponse(
        List<TopAreaResponse> atmosphere,
        List<TopAreaResponse> infra,
        List<TopAreaResponse> clean,
        List<TopAreaResponse> size,
        List<TopAreaResponse> access
) {

    /**
     * 내부 Top 5를 API 응답으로 변환한다.
     *
     * @param topFive Service의 내부 Top 5 모델
     * @return 각 Area를 API DTO로 변환한 Top 5 응답
     */
    public static TopFiveResponse from(TopFive topFive) {
        return new TopFiveResponse(
                responses(topFive.atmosphere()),
                responses(topFive.infra()),
                responses(topFive.clean()),
                responses(topFive.size()),
                responses(topFive.access())
        );
    }

    private static List<TopAreaResponse> responses(
            List<TopArea> areas
    ) {
        return areas.stream().map(TopAreaResponse::from).toList();
    }
}
