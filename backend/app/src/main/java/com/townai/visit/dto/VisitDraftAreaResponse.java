package com.townai.visit.dto;

/**
 * Parser가 활성 Area 목록에서 명확하게 식별한 Area의 최소 정보이다.
 *
 * @param id Area 식별자
 * @param name 사용자 확인용 Area 이름
 */
public record VisitDraftAreaResponse(
        Long id,
        String name
) {
}
