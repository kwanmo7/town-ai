package com.townai.report.generation;

import java.util.List;

/**
 * compare-v1 JSON Schema로 받은 검증 전 Structured Output이다.
 *
 * @param criteria 다섯 평가 항목별 비교 문장
 * @param areaAssessments 입력 순서와 일치해야 하는 지역별 장단점
 * @param verificationChecklist 사용자가 현장에서 추가 확인할 객관 항목
 * @param overall 최대 600자의 종합 평가
 */
record CompareAiOutput(
        Criteria criteria,
        List<AreaAssessment> areaAssessments,
        List<VerificationItem> verificationChecklist,
        String overall
) {

    /**
     * 각 문장은 최대 300자이며 Backend가 같은 이름의 점수 열과 함께 표시한다.
     */
    record Criteria(
            String atmosphere,
            String infra,
            String clean,
            String size,
            String access
    ) {
    }

    /**
     * @param areaId 입력 Area ID
     * @param areaName 입력 Area 이름
     * @param content 해당 Area의 최대 300자 평가
     */
    record AreaAssessment(Long areaId, String areaName, String content) {
    }

    /**
     * @param category 확인 항목 분류
     * @param content 사용자가 직접 확인할 구체적인 내용
     */
    record VerificationItem(String category, String content) {
    }
}
