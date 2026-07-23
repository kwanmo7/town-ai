package com.townai.report.dto;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;

import java.time.Instant;

/**
 * Report 생성 및 목록 조회에 사용하는 메타데이터 응답이다.
 *
 * @param id Report 식별자
 * @param reportType 생성된 Report 유형
 * @param model 생성에 실제 사용된 OpenAI 모델
 * @param promptVersion 적용된 Prompt 버전
 * @param createdAt Report 생성 시각
 */
public record ReportResponse(
        Long id,
        ReportType reportType,
        String model,
        String promptVersion,
        Instant createdAt
) {

    /**
     * Report Entity를 생성·목록용 응답으로 변환한다.
     *
     * @param report 변환할 Report Entity
     * @return Storage 경로를 제외한 공개 메타데이터
     */
    public static ReportResponse from(ReportEntity report) {
        return new ReportResponse(
                report.getId(),
                report.getReportType(),
                report.getModel(),
                report.getPromptVersion(),
                report.getCreatedAt()
        );
    }
}
