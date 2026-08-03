package com.townai.visit.dto;

/**
 * Parser가 식별한 기존 Area 또는 새로 등록할 Area 후보이다.
 *
 * <p>{@code id}가 있으면 Backend에 등록된 활성 Area이고, {@code null}이면
 * 사용자가 확인한 뒤 등록할 수 있는 새 Area 후보이다.</p>
 *
 * @param id 기존 Area 식별자. 신규 후보이면 {@code null}
 * @param name 사용자 확인용 Area 이름
 * @param prefecture 도도부현 이름. 확정할 수 없으면 {@code null}
 * @param city 시구정촌 이름. 확정할 수 없으면 {@code null}
 * @param station 선택적인 인접 역 이름
 */
public record VisitDraftAreaResponse(
        Long id,
        String name,
        String prefecture,
        String city,
        String station
) {

    /**
     * 기존 활성 Area와 연결된 결과인지 반환한다.
     *
     * @return 기존 Area이면 {@code true}
     */
    public boolean registered() {
        return id != null;
    }

    /**
     * 새 Area 등록에 필요한 위치 필드가 모두 있는지 반환한다.
     *
     * @return 이름, 도도부현과 시구정촌이 모두 있으면 {@code true}
     */
    public boolean hasRequiredLocation() {
        return hasText(name) && hasText(prefecture) && hasText(city);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
