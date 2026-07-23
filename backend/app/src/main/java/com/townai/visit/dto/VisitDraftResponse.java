package com.townai.visit.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 사용자가 확인한 뒤 Visit 등록 요청으로 사용할 수 있는 파싱 초안이다.
 *
 * <p>AI가 입력만으로 확정할 수 없는 값은 {@code null}이며, 그 이유가
 * {@code warnings}에 포함된다. 이 객체 자체는 DB에 저장되지 않는다.</p>
 *
 * @param area 명확히 식별된 활성 Area. 확정할 수 없으면 {@code null}
 * @param visitDate 해석된 방문일. 확정할 수 없으면 {@code null}
 * @param atmosphereScore 분위기 점수 후보
 * @param infraScore 생활 인프라 점수 후보
 * @param cleanScore 청결도 점수 후보
 * @param sizeScore 넓은 집 가능성 점수 후보
 * @param accessScore 접근성 점수 후보
 * @param memo 정리된 메모 후보
 * @param warnings 누락되거나 모호해 사용자가 확인해야 하는 항목
 */
public record VisitDraftResponse(
        VisitDraftAreaResponse area,
        LocalDate visitDate,
        Integer atmosphereScore,
        Integer infraScore,
        Integer cleanScore,
        Integer sizeScore,
        Integer accessScore,
        String memo,
        List<String> warnings
) {
}
