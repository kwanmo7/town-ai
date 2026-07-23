package com.townai.report.entity;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;

import java.util.Locale;

/**
 * 지원하는 Report 유형과 유형별 Prompt Version을 정의한다.
 *
 * <p>유형 문자열은 API, Prompt Resource 경로와 Storage 경로에서 공통으로 사용한다.</p>
 */
public enum ReportType {

    /** DB 집계와 항목별 Top 5에 최대 500자의 짧은 AI Comment를 더하는 요약. */
    SUMMARY("summary-v1"),

    /** 모든 활성 Area와 Visit을 대상으로 작성하는 전체 상세 분석. */
    ALL("all-v1"),

    /** 활성 Area 한 곳의 Visit 이력을 집중 분석하는 상세 Report. */
    AREA("area-v1"),

    /** 사용자가 선택한 2~5개 활성 Area를 같은 항목으로 비교하는 Report. */
    COMPARE("compare-v1");

    private final String promptVersion;

    ReportType(String promptVersion) {
        this.promptVersion = promptVersion;
    }

    /**
     * 이 유형의 Prompt 버전을 반환한다.
     *
     * @return 생성 메타데이터에 저장할 유형별 Prompt 버전
     */
    public String promptVersion() {
        return promptVersion;
    }

    /**
     * 경로에 사용할 유형 이름을 반환한다.
     *
     * @return Prompt와 Storage 경로에 사용할 Locale 비의존 소문자 이름
     */
    public String pathName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * API 문자열을 대소문자와 관계없이 Report 유형으로 변환한다.
     *
     * @param value 요청에서 전달된 Report 유형
     * @return 지원하는 ReportType
     * @throws ApiException 값이 없거나 지원하지 않는 유형인 경우
     */
    public static ReportType from(String value) {
        if (value == null) {
            throw new ApiException(ErrorCode.UNSUPPORTED_REPORT_TYPE);
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ErrorCode.UNSUPPORTED_REPORT_TYPE);
        }
    }
}
