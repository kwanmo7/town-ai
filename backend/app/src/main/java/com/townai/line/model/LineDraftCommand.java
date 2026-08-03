package com.townai.line.model;

/**
 * 검증된 LINE Draft Postback 명령이다.
 *
 * @param action 저장, 수정 또는 취소 동작
 * @param draftId 대상 LINE Visit Draft ID
 */
public record LineDraftCommand(
        LineDraftAction action,
        Long draftId
) implements LinePostbackCommand {
}
