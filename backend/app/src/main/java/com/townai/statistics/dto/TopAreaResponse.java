package com.townai.statistics.dto;

import com.townai.statistics.model.TopArea;

/**
 * 평가 항목별 Top 5에 포함되는 Area의 API 표현이다.
 *
 * @param areaId Area 식별자
 * @param areaName 사용자에게 표시할 Area 이름
 * @param score 해당 평가 항목의 Visit 평균
 */
public record TopAreaResponse(
        Long areaId,
        String areaName,
        Double score
) {

    /**
     * 내부 Top Area를 API 응답으로 변환한다.
     *
     * @param area Service의 내부 Top Area 모델
     * @return API Top Area 응답
     */
    public static TopAreaResponse from(TopArea area) {
        return new TopAreaResponse(
                area.areaId(),
                area.areaName(),
                area.score()
        );
    }
}
