package com.townai.line.entity;

/**
 * LINE Webhook 이벤트의 비동기 처리 상태이다.
 */
public enum LineWebhookEventStatus {

    /** Webhook 수신을 완료하고 비동기 처리를 기다리는 상태. */
    RECEIVED,

    /** 내부 처리기가 이벤트를 점유해 처리 중인 상태. */
    PROCESSING,

    /** 처리 결과의 DB 저장과 LINE Push 수락까지 완료된 상태. */
    COMPLETED,

    /** 재시도할 수 없는 오류이거나 최대 처리 횟수에 도달한 상태. */
    FAILED
}
