package com.townai.report.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * Report 유형과 분석 대상을 전달하는 생성 요청이다.
 *
 * <p>{@code SUMMARY}/{@code ALL}은 {@code areaIds} 필드 자체가 없어야 하고,
 * {@code AREA}/{@code COMPARE}는 유형에 맞는 개수의 ID가 필요하다. Jackson Setter가
 * 호출됐는지를 별도 기록해 필드 생략과 명시적 {@code null}을 구분한다.</p>
 */
public class ReportCreateRequest {

    @NotBlank(message = "리포트 유형은 필수입니다.")
    private String reportType;

    private List<Long> areaIds;
    private boolean areaIdsProvided;

    /**
     * 요청한 Report 유형을 반환한다.
     *
     * @return 요청한 Report 유형 문자열
     */
    public String getReportType() {
        return reportType;
    }

    /**
     * 요청할 Report 유형을 설정한다.
     *
     * @param reportType 요청할 Report 유형
     */
    public void setReportType(String reportType) {
        this.reportType = reportType;
    }

    /**
     * 요청에서 지정한 Area 목록을 반환한다.
     *
     * @return 사용자가 지정한 Area ID 목록
     */
    public List<Long> getAreaIds() {
        return areaIds;
    }

    /**
     * JSON에 {@code areaIds}가 있으면 값과 관계없이 필드 제공 상태를 기록한다.
     *
     * @param areaIds 분석 대상 Area ID 목록 또는 명시적인 {@code null}
     */
    @JsonProperty("areaIds")
    public void setAreaIds(List<Long> areaIds) {
        this.areaIds = areaIds;
        this.areaIdsProvided = true;
    }

    /**
     * JSON에 Area 목록 필드가 있었는지 반환한다.
     *
     * @return JSON 요청에 {@code areaIds} 필드가 존재했으면 {@code true}
     */
    public boolean isAreaIdsProvided() {
        return areaIdsProvided;
    }
}
