package com.townai.line.entity;

/**
 * LINE 자연어 Visit Draft의 확인 상태이다.
 */
public enum LineVisitDraftStatus {

    /** 필수 값이 누락되거나 모호해 새 자연어 입력이 필요한 상태. */
    NEEDS_INPUT,

    /** 필수 값이 모두 존재해 사용자의 확인 또는 취소를 기다리는 상태. */
    AWAITING_CONFIRMATION,

    /** 사용자가 확인해 Visit 저장까지 완료된 상태. */
    CONFIRMED,

    /** 사용자가 저장을 취소한 상태. */
    CANCELLED,

    /** 생성 후 24시간이 지나 확인할 수 없는 상태. */
    EXPIRED
}
