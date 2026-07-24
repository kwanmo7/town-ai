package com.townai.line.processing;

/**
 * 내부 Task Endpoint가 Cloud Tasks 또는 Local Dispatcher에 반환할 결과이다.
 */
public enum LineEventTaskResult {

    /** 처리 완료, 최종 실패 또는 이미 종료된 이벤트로 2xx 응답해야 함. */
    ACKNOWLEDGED,

    /** 일시적인 실패 또는 활성 Lease 때문에 5xx 재시도가 필요함. */
    RETRY
}
