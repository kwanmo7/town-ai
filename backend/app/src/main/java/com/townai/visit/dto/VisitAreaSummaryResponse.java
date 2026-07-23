package com.townai.visit.dto;

import com.townai.area.entity.AreaEntity;

/**
 * Visit 목록과 생성·수정 응답에 포함되는 Area 요약이다.
 *
 * @param id Area 식별자
 * @param name 사용자에게 표시할 Area 이름
 */
public record VisitAreaSummaryResponse(
        Long id,
        String name
) {

    /**
     * Area Entity를 Visit 요약용 Area 응답으로 변환한다.
     *
     * @param area Visit과 연결된 Area Entity
     * @return ID와 이름만 포함한 Area 요약
     */
    public static VisitAreaSummaryResponse from(AreaEntity area) {
        return new VisitAreaSummaryResponse(area.getId(), area.getName());
    }
}
