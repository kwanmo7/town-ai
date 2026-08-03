package com.townai.line.entity;

/**
 * LINE 자연어 Visit Draft의 확인 상태이다.
 */
public enum LineVisitDraftStatus {

    /** 필수 값이 누락되거나 모호해 새 자연어 입력이 필요한 상태. */
    NEEDS_INPUT,

    /** 필수 값이 모두 존재해 사용자의 저장, 수정 또는 취소를 기다리는 상태. */
    AWAITING_CONFIRMATION,

    /** 사용자가 수정 버튼을 누르고 다음 자연어 수정 내용을 기다리는 상태. */
    AWAITING_REVISION,

    /** 하나의 Text Message가 수정 대상으로 점유되어 AI 파싱 중인 상태. */
    REVISION_PROCESSING,

    /** 수정된 새 Draft로 대체되어 더 이상 처리할 수 없는 상태. */
    SUPERSEDED,

    /** 사용자가 확인해 Visit 저장까지 완료된 상태. */
    CONFIRMED,

    /** 사용자가 저장을 취소한 상태. */
    CANCELLED,

    /** 생성 후 24시간이 지나 확인할 수 없는 상태. */
    EXPIRED
}
