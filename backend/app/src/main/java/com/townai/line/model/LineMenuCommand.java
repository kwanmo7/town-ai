package com.townai.line.model;

/**
 * LINE 고정 메뉴 화면 이동 명령이다.
 *
 * @param target 표시할 메뉴 화면
 */
public record LineMenuCommand(
        LineMenuTarget target
) implements LinePostbackCommand {
}
