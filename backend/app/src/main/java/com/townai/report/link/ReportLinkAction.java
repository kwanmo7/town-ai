package com.townai.report.link;

/**
 * 서명 URL이 허용하는 Report 접근 동작을 구분한다.
 */
public enum ReportLinkAction {

    /** 브라우저에서 Markdown 본문을 표시한다. */
    CONTENT("content"),

    /** Markdown 파일을 attachment로 다운로드한다. */
    DOWNLOAD("download");

    private final String pathSegment;

    ReportLinkAction(String pathSegment) {
        this.pathSegment = pathSegment;
    }

    /**
     * 공개 Endpoint에서 사용하는 경로 조각을 반환한다.
     *
     * @return {@code content} 또는 {@code download}
     */
    public String pathSegment() {
        return pathSegment;
    }
}
