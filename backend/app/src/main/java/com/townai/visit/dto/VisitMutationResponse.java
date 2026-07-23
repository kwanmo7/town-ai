package com.townai.visit.dto;

import com.townai.visit.entity.VisitEntity;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Visit 생성과 수정 결과에 사용하는 응답이다.
 *
 * @param id Visit 식별자
 * @param area 연결된 Area의 요약
 * @param visitDate 실제 방문일
 * @param atmosphereScore 분위기 점수
 * @param infraScore 생활 인프라 점수
 * @param cleanScore 청결도 점수
 * @param sizeScore 넓은 집 가능성 점수
 * @param accessScore 접근성 점수
 * @param memo 방문 메모
 * @param createdAt 생성 시각
 * @param updatedAt 마지막 수정 시각
 */
public record VisitMutationResponse(
        Long id,
        VisitAreaSummaryResponse area,
        LocalDate visitDate,
        int atmosphereScore,
        int infraScore,
        int cleanScore,
        int sizeScore,
        int accessScore,
        String memo,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Visit Entity를 생성·수정 응답으로 변환한다.
     *
     * @param visit 저장 또는 수정된 Visit Entity
     * @return 생성·수정 API 응답
     */
    public static VisitMutationResponse from(VisitEntity visit) {
        return new VisitMutationResponse(
                visit.getId(),
                VisitAreaSummaryResponse.from(visit.getArea()),
                visit.getVisitDate(),
                visit.getAtmosphereScore(),
                visit.getInfraScore(),
                visit.getCleanScore(),
                visit.getSizeScore(),
                visit.getAccessScore(),
                visit.getMemo(),
                visit.getCreatedAt(),
                visit.getUpdatedAt()
        );
    }
}
