package com.townai.report.dto;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;

import java.time.Instant;
import java.util.List;

/**
 * Report 상세 조회 응답이다.
 *
 * @param id Report 식별자
 * @param reportType Report 유형
 * @param areaIds 생성 당시 분석 대상 Area ID. 표시 순서대로 정렬됨
 * @param model 생성에 실제 사용된 OpenAI 모델
 * @param promptVersion 적용된 Prompt 버전
 * @param createdAt Report 생성 시각
 */
public record ReportDetailResponse(
        Long id,
        ReportType reportType,
        List<Long> areaIds,
        String model,
        String promptVersion,
        Instant createdAt
) {

    /**
     * Report Entity와 연결 Row에서 상세 응답을 조립한다.
     *
     * @param report 변환할 Report Entity
     * @param areaIds 표시 순서로 조회한 생성 당시 대상 Area ID
     * @return Storage 경로를 제외한 상세 응답
     */
    public static ReportDetailResponse from(ReportEntity report, List<Long> areaIds) {
        return new ReportDetailResponse(
                report.getId(),
                report.getReportType(),
                List.copyOf(areaIds),
                report.getModel(),
                report.getPromptVersion(),
                report.getCreatedAt()
        );
    }
}
