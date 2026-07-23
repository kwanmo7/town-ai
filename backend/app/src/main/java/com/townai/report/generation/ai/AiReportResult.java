package com.townai.report.generation.ai;

/**
 * Report AI Adapter가 반환하는 검증 전 원본 결과이다.
 *
 * @param model OpenAI 응답이 실제로 사용한 모델
 * @param output 유형별 Validator가 검사할 원본 JSON 또는 Markdown
 */
public record AiReportResult(String model, String output) {
}
