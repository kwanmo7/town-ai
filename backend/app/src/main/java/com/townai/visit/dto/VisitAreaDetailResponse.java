package com.townai.visit.dto;

import com.townai.area.entity.AreaEntity;

/**
 * Visit 상세 응답에 포함되는 Area 정보이다.
 *
 * @param id Area 식별자
 * @param name 동네 이름
 * @param prefecture 도도부현 이름
 * @param city 시구정촌 이름
 * @param station 인접 역 이름. 등록되지 않았으면 {@code null}
 */
public record VisitAreaDetailResponse(
        Long id,
        String name,
        String prefecture,
        String city,
        String station
) {

    /**
     * Area Entity를 Visit 상세용 Area 응답으로 변환한다.
     *
     * @param area Visit과 연결된 Area Entity
     * @return 상세 화면에 필요한 Area 응답
     */
    public static VisitAreaDetailResponse from(AreaEntity area) {
        return new VisitAreaDetailResponse(
                area.getId(),
                area.getName(),
                area.getPrefecture(),
                area.getCity(),
                area.getStation()
        );
    }
}
