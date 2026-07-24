package com.townai.line.messaging;

/**
 * 한 Webhook 이벤트에서 전송할 수 있는 LINE Push Message 용도이다.
 */
public enum LineMessagePurpose {

    /** Text Message 파싱 후 Draft와 확인 동작을 안내하는 메시지. */
    DRAFT_RESULT,

    /** Draft 확인과 Visit 저장 결과 메시지. */
    CONFIRM_RESULT,

    /** Draft 취소 결과 메시지. */
    CANCEL_RESULT,

    /** 최대 재시도 후 사용자에게 알리는 실패 메시지. */
    FAILURE_NOTICE
}
