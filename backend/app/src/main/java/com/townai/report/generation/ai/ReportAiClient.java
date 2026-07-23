package com.townai.report.generation.ai;

import com.townai.report.entity.ReportType;

/**
 * Report 생성 모델 호출을 생성 Use Case와 분리하는 Port이다.
 */
public interface ReportAiClient {

    /**
     * 유형별 Prompt 입력으로 AI 원본 출력을 생성한다.
     *
     * @param reportType Prompt와 출력 형식을 결정할 Report 유형
     * @param promptInput Backend가 조립한 유형별 입력
     * @param correctionInstruction 이전 출력의 검증 실패 사유. 최초 호출이면 {@code null}
     * @return 실제 모델명과 검증 전 원본 출력
     */
    AiReportResult generate(
            ReportType reportType,
            Object promptInput,
            String correctionInstruction
    );
}
