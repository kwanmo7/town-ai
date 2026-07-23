package com.townai.report.generation;

/**
 * summary-v1 JSON Schema로 받은 검증 전 Structured Output이다.
 *
 * @param comment SQL 통계를 바탕으로 작성한 최대 500자의 짧은 AI 평가
 */
record SummaryAiOutput(String comment) {
}
