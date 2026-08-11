package com.townai.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * 자연어 Visit 초안 생성 요청이다.
 *
 * @param text Area, 날짜와 메모 후보를 추출할 자연어
 * @param atmosphereScore Web에서 사용자가 직접 선택한 선택적 분위기 점수
 * @param infraScore Web에서 사용자가 직접 선택한 선택적 생활 인프라 점수
 * @param cleanScore Web에서 사용자가 직접 선택한 선택적 청결도 점수
 * @param sizeScore Web에서 사용자가 직접 선택한 선택적 넓은 집 가능성 점수
 * @param accessScore Web에서 사용자가 직접 선택한 선택적 접근성 점수
 */
public record VisitDraftRequest(
        @NotBlank(message = "자연어 방문 평가는 필수입니다.")
        String text,

        @Min(value = 0, message = "분위기 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "분위기 점수는 10 이하여야 합니다.")
        Integer atmosphereScore,

        @Min(value = 0, message = "생활 인프라 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "생활 인프라 점수는 10 이하여야 합니다.")
        Integer infraScore,

        @Min(value = 0, message = "청결 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "청결 점수는 10 이하여야 합니다.")
        Integer cleanScore,

        @Min(value = 0, message = "넓은 집 가능성 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "넓은 집 가능성 점수는 10 이하여야 합니다.")
        Integer sizeScore,

        @Min(value = 0, message = "접근성 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "접근성 점수는 10 이하여야 합니다.")
        Integer accessScore
) {

    /**
     * 점수를 자연어에 포함하는 LINE 등 기존 호출자를 위한 생성자이다.
     *
     * @param text Area, 날짜, 점수와 메모 후보를 추출할 자연어
     */
    public VisitDraftRequest(String text) {
        this(text, null, null, null, null, null);
    }
}
