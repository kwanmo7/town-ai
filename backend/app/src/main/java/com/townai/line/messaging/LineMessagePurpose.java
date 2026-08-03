package com.townai.line.messaging;

/**
 * 한 Webhook 이벤트에서 전송할 수 있는 LINE Push Message 용도이다.
 */
public enum LineMessagePurpose {

    /** Follow 또는 메뉴 이동 요청에 따라 표시하는 메뉴 메시지. */
    MENU_RESULT,

    /** Report 대상 선택 화면. */
    REPORT_SELECTION,

    /** AI Report 생성 시작 안내. */
    REPORT_GENERATING,

    /** 생성된 Report 조회·다운로드 안내. */
    REPORT_RESULT,

    /** Text Message 파싱 후 Draft와 확인 동작을 안내하는 메시지. */
    DRAFT_RESULT,

    /** Draft 확인과 Visit 저장 결과 메시지. */
    CONFIRM_RESULT,

    /** Draft 수정 입력을 요청하는 메시지. */
    EDIT_RESULT,

    /** Draft 취소 결과 메시지. */
    CANCEL_RESULT,

    /** 최대 재시도 후 사용자에게 알리는 실패 메시지. */
    FAILURE_NOTICE
}
