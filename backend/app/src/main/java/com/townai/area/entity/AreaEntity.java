package com.townai.area.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 평가 대상 지역의 위치 정보와 논리 삭제 상태를 표현하는 Domain Entity이다.
 *
 * <p>동일 지역은 {@code prefecture + city + name} 조합으로 식별한다. 삭제 후에도
 * Visit 이력과 중복 방지 규칙을 보존하므로 Row를 제거하지 않고 {@code deletedAt}을
 * 기록한다. 임의 상태 변경을 막기 위해 Setter를 제공하지 않으며 수정과 삭제는
 * 도메인 메서드로만 처리한다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AreaEntity {

    private Long id;

    private String name;

    private String prefecture;

    private String city;

    private String station;

    private Instant createdAt;

    private Instant updatedAt;

    private Instant deletedAt;

    @Builder
    private AreaEntity(String name, String prefecture, String city, String station) {
        this.name = name;
        this.prefecture = prefecture;
        this.city = city;
        this.station = station;
    }

    public static AreaEntity restore(
            Long id,
            String name,
            String prefecture,
            String city,
            String station,
            Instant createdAt,
            Instant updatedAt,
            Instant deletedAt
    ) {
        AreaEntity area = new AreaEntity(name, prefecture, city, station);
        area.id = id;
        area.createdAt = createdAt;
        area.updatedAt = updatedAt;
        area.deletedAt = deletedAt;
        return area;
    }

    public void markPersisted(Long persistedId, Instant persistedAt) {
        if (id == null) {
            id = persistedId;
            createdAt = persistedAt;
        }
        updatedAt = persistedAt;
    }

    /**
     * PUT 요청으로 전달된 Area 전체 값을 교체한다.
     *
     * @param name 변경할 동네 이름
     * @param prefecture 변경할 도도부현 이름
     * @param city 변경할 시구정촌 이름
     * @param station 변경할 인접 역 이름. 등록하지 않으면 {@code null}
     */
    public void update(String name, String prefecture, String city, String station) {
        this.name = name;
        this.prefecture = prefecture;
        this.city = city;
        this.station = station;
    }

    /**
     * 방문 기록을 보존하면서 Area만 외부 조회 대상에서 제외한다.
     *
     * @param deletedAt 삭제가 확정된 UTC 시각
     */
    public void softDelete(Instant deletedAt) {
        this.deletedAt = deletedAt;
    }
}
