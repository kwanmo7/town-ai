package com.townai.statistics.model;

/**
 * 평가 항목별 Top 5에 포함되는 Area 통계이다.
 *
 * @param areaId Area 식별자
 * @param areaName 사용자에게 표시할 Area 이름
 * @param score 해당 항목의 반올림된 Visit 평균
 */
public record TopArea(
        Long areaId,
        String areaName,
        Double score
) {
}
