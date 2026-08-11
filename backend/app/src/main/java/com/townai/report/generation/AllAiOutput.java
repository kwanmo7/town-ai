package com.townai.report.generation;

import java.util.List;

/**
 * all-v1 JSON Schema로 받은 검증 전 Structured Output이다.
 *
 * @param overallTrends 전체 후보군에서 관찰되는 공통 경향
 * @param areaAnalyses 입력 순서와 일치해야 하는 Area별 상세 분석
 * @param criteriaCandidates 다섯 평가 항목별 주요 후보 분석
 * @param priorityCandidates 다섯 평가 항목을 우선할 때의 조건부 후보 분석
 * @param verificationChecklist 사용자가 추가로 직접 확인할 객관 항목
 * @param overall 전체 후보군에 대한 종합 평가
 */
record AllAiOutput(
        String overallTrends,
        List<AreaAnalysis> areaAnalyses,
        CriteriaAnalysis criteriaCandidates,
        CriteriaAnalysis priorityCandidates,
        List<VerificationItem> verificationChecklist,
        String overall
) {

    /**
     * @param areaId 입력 Area ID
     * @param areaName 입력 Area 이름
     * @param summary 점수와 Visit을 함께 고려한 평가 요약
     * @param strengths 주요 장점
     * @param weaknesses 주요 단점
     * @param considerations 해석 한계와 선택 시 고려사항
     */
    record AreaAnalysis(
            Long areaId,
            String areaName,
            String summary,
            String strengths,
            String weaknesses,
            String considerations
    ) {
    }

    /**
     * 다섯 평가 항목별 조건부 후보 분석을 보존한다.
     */
    record CriteriaAnalysis(
            String atmosphere,
            String infra,
            String clean,
            String size,
            String access
    ) {
    }

    /**
     * @param category 확인 항목 분류
     * @param content 사용자가 직접 확인할 구체적인 내용
     */
    record VerificationItem(String category, String content) {
    }
}
