package com.townai.report.generation;

import com.townai.area.entity.AreaEntity;
import com.townai.report.entity.ReportType;

import java.util.List;

/**
 * 요청 검증과 DB 조회를 마친 Report 생성 입력 묶음이다.
 *
 * @param reportType 생성할 Report 유형
 * @param targetAreas 생성 당시 분석 대상 Area. 요청 표시 순서를 보존함
 * @param promptInput 유형별 Prompt에 직렬화할 입력 모델
 */
public record ReportGenerationData(
        ReportType reportType,
        List<AreaEntity> targetAreas,
        Object promptInput
) {

    /**
     * 대상 Area 순서가 이후 변경되지 않도록 불변 목록으로 복사한다.
     */
    public ReportGenerationData {
        targetAreas = List.copyOf(targetAreas);
    }
}
