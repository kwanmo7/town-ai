package com.townai.visit.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Visit 생성과 전체 수정(PUT)에 공통으로 사용하는 요청이다.
 *
 * @param areaId 방문한 활성 Area ID
 * @param visitDate 실제 방문일
 * @param atmosphereScore 분위기 점수. 0~10 정수
 * @param infraScore 생활 인프라 점수. 0~10 정수
 * @param cleanScore 청결도 점수. 0~10 정수
 * @param sizeScore 넓은 집 가능성 점수. 0~10 정수
 * @param accessScore 접근성 점수. 0~10 정수
 * @param memo 선택적인 방문 메모
 */
public record VisitRequest(
        @NotNull(message = "지역 ID는 필수입니다.")
        Long areaId,

        @NotNull(message = "방문일은 필수입니다.")
        LocalDate visitDate,

        @NotNull(message = "분위기 점수는 필수입니다.")
        @Min(value = 0, message = "분위기 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "분위기 점수는 10 이하여야 합니다.")
        Integer atmosphereScore,

        @NotNull(message = "생활 인프라 점수는 필수입니다.")
        @Min(value = 0, message = "생활 인프라 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "생활 인프라 점수는 10 이하여야 합니다.")
        Integer infraScore,

        @NotNull(message = "청결 점수는 필수입니다.")
        @Min(value = 0, message = "청결 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "청결 점수는 10 이하여야 합니다.")
        Integer cleanScore,

        @NotNull(message = "넓은 집 가능성 점수는 필수입니다.")
        @Min(value = 0, message = "넓은 집 가능성 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "넓은 집 가능성 점수는 10 이하여야 합니다.")
        Integer sizeScore,

        @NotNull(message = "접근성 점수는 필수입니다.")
        @Min(value = 0, message = "접근성 점수는 0 이상이어야 합니다.")
        @Max(value = 10, message = "접근성 점수는 10 이하여야 합니다.")
        Integer accessScore,

        String memo
) {
}
