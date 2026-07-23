package com.townai.area.dto;

import com.townai.area.entity.AreaEntity;

import java.time.Instant;

/**
 * Area 생성, 상세 조회 및 수정 결과에 사용하는 응답이다.
 *
 * @param id Area 식별자
 * @param name 동네 이름
 * @param prefecture 도도부현 이름
 * @param city 시구정촌 이름
 * @param station 인접 역 이름. 등록되지 않았으면 {@code null}
 * @param createdAt 생성 시각
 * @param updatedAt 마지막 수정 시각
 */
public record AreaDetailResponse(
        Long id,
        String name,
        String prefecture,
        String city,
        String station,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * 영속 Entity를 외부 상세 응답으로 변환한다.
     *
     * @param area 변환할 Area Entity
     * @return Entity의 현재 값을 복사한 응답
     */
    public static AreaDetailResponse from(AreaEntity area) {
        return new AreaDetailResponse(
                area.getId(),
                area.getName(),
                area.getPrefecture(),
                area.getCity(),
                area.getStation(),
                area.getCreatedAt(),
                area.getUpdatedAt()
        );
    }
}
