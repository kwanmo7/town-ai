package com.townai.line.model;

/**
 * Town AI V1이 처리하고 DB에 저장하는 LINE Webhook 이벤트 유형이다.
 */
public enum LineWebhookEventType {

    /** 친구 추가 또는 차단 해제 후 메인 메뉴를 안내할 Follow Event. */
    FOLLOW,

    /** 자연어 Visit Draft를 생성할 Text Message Event. */
    TEXT_MESSAGE,

    /** 저장 확인 또는 취소를 요청하는 Postback Event. */
    POSTBACK
}
