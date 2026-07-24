package com.townai.line.processing;

/**
 * 내부 Task가 LINE 이벤트를 점유한 결과이다.
 */
public enum LineEventClaimStatus {

    /** 현재 Task가 새 처리 Lease를 획득함. */
    ACQUIRED,

    /** 다른 처리 시도의 3분 Lease가 아직 유효함. */
    BUSY,

    /** 이벤트가 이미 완료 또는 최종 실패 상태임. */
    TERMINAL,

    /** 전달된 Webhook Event ID에 대응하는 DB Row가 없음. */
    MISSING
}
