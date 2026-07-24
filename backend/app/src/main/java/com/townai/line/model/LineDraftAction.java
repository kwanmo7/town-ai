package com.townai.line.model;

/**
 * LINE Draft Postback에서 지원하는 사용자 동작이다.
 */
public enum LineDraftAction {

    /** Draft를 Visit으로 저장하는 동작. */
    CONFIRM,

    /** Draft 저장을 취소하는 동작. */
    CANCEL
}
