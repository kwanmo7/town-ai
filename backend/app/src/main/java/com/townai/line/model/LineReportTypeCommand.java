package com.townai.line.model;

import com.townai.report.entity.ReportType;

/**
 * LINE에서 생성할 Report 유형을 선택한 명령이다.
 *
 * @param reportType 선택한 Report 유형
 */
public record LineReportTypeCommand(
        ReportType reportType
) implements LinePostbackCommand {
}
