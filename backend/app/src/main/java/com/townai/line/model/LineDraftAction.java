package com.townai.line.model;

/**
 * LINE Draft Postback에서 지원하는 사용자 동작이다.
 */
public enum LineDraftAction {

    /** Draft를 Visit으로 저장하는 동작. */
    CONFIRM,

    /** 다음 Text Message로 Draft 일부 또는 전체를 수정하는 동작. */
    EDIT,

    /** Draft 저장을 취소하는 동작. */
    CANCEL
}
