package com.townai.line.model;

/**
 * 검증된 LINE Postback 동작의 공통 타입이다.
 */
public sealed interface LinePostbackCommand
        permits LineCompareToggleCommand, LineDraftCommand,
        LineMenuCommand, LineReportGenerateCommand,
        LineReportTypeCommand {
}
