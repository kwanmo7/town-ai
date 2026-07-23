package com.townai.area.dto;

import com.townai.area.entity.AreaEntity;

/**
 * Area 목록에서 사용하는 요약 응답이다.
 *
 * <p>목록에 필요하지 않은 생성 및 수정 시각은 포함하지 않는다.</p>
 *
 * @param id Area 식별자
 * @param name 동네 이름
 * @param prefecture 도도부현 이름
 * @param city 시구정촌 이름
 * @param station 인접 역 이름. 등록되지 않았으면 {@code null}
 */
public record AreaSummaryResponse(
        Long id,
        String name,
        String prefecture,
        String city,
        String station
) {

    /**
     * 영속 Entity를 목록용 응답으로 변환한다.
     *
     * @param area 변환할 Area Entity
     * @return 목록에 필요한 필드만 복사한 응답
     */
    public static AreaSummaryResponse from(AreaEntity area) {
        return new AreaSummaryResponse(
                area.getId(),
                area.getName(),
                area.getPrefecture(),
                area.getCity(),
                area.getStation()
        );
    }
}
