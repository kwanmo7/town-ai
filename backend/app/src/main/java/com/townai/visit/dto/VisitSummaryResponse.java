package com.townai.visit.dto;

import com.townai.visit.entity.VisitEntity;

import java.time.LocalDate;

/**
 * Visit 목록에서 사용하는 요약 응답이다.
 *
 * <p>목록에 불필요한 메모와 생성·수정 시각은 포함하지 않는다.</p>
 *
 * @param id Visit 식별자
 * @param area 연결된 Area의 요약
 * @param visitDate 실제 방문일
 * @param atmosphereScore 분위기 점수
 * @param infraScore 생활 인프라 점수
 * @param cleanScore 청결도 점수
 * @param sizeScore 넓은 집 가능성 점수
 * @param accessScore 접근성 점수
 */
public record VisitSummaryResponse(
        Long id,
        VisitAreaSummaryResponse area,
        LocalDate visitDate,
        int atmosphereScore,
        int infraScore,
        int cleanScore,
        int sizeScore,
        int accessScore
) {

    /**
     * Visit Entity를 목록용 응답으로 변환한다.
     *
     * @param visit 목록에 포함할 Visit Entity
     * @return 목록에 필요한 필드만 복사한 응답
     */
    public static VisitSummaryResponse from(VisitEntity visit) {
        return new VisitSummaryResponse(
                visit.getId(),
                VisitAreaSummaryResponse.from(visit.getArea()),
                visit.getVisitDate(),
                visit.getAtmosphereScore(),
                visit.getInfraScore(),
                visit.getCleanScore(),
                visit.getSizeScore(),
                visit.getAccessScore()
        );
    }
}
