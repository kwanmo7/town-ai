package com.townai.line.processing;

/**
 * 처리 실패를 DB에 반영한 뒤 Task가 취할 동작이다.
 */
public enum LineEventFailureResult {

    /** 이벤트를 {@code RECEIVED}로 되돌렸으므로 Task를 재시도해야 함. */
    RETRY,

    /** 이벤트를 {@code FAILED}로 종료했으므로 Task 재시도가 필요 없음. */
    TERMINAL,

    /** 더 새로운 시도가 이벤트를 점유해 현재 실패 결과를 반영하지 않음. */
    STALE
}
