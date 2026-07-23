package com.townai.visit.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 자연어 Visit 초안 생성 요청이다.
 *
 * @param text Area, 날짜, 점수와 메모 후보를 추출할 자연어
 */
public record VisitDraftRequest(
        @NotBlank(message = "자연어 방문 평가는 필수입니다.")
        String text
) {
}
