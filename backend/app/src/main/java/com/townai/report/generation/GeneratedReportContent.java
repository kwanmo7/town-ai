package com.townai.report.generation;

/**
 * 검증과 Markdown 조립을 마친 최종 Report 결과이다.
 *
 * @param model 응답이 실제로 사용한 OpenAI 모델
 * @param promptVersion Report 유형에 적용한 Prompt 버전
 * @param markdown Storage에 바로 기록할 수 있는 최종 Markdown
 */
public record GeneratedReportContent(
        String model,
        String promptVersion,
        String markdown
) {
}
