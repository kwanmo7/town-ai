package com.townai.report.generation;

/**
 * AI 출력이 Report 계약을 지키지 못했을 때 한 차례 교정을 요청하기 위한 내부 예외이다.
 *
 * <p>사용자 입력 오류가 아니라 모델 출력 오류이므로 Controller까지 직접 전달하지 않는다.</p>
 */
class InvalidReportOutputException extends RuntimeException {

    InvalidReportOutputException(String message) {
        super(message);
    }
}
