package com.townai.line.model;

import com.townai.report.entity.ReportType;

import java.util.List;

/**
 * 선택한 대상을 사용해 LINE Report 생성을 요청하는 명령이다.
 *
 * @param reportType 생성할 Report 유형
 * @param areaIds AREA·COMPARE 대상 ID. SUMMARY·ALL이면 빈 목록
 */
public record LineReportGenerateCommand(
        ReportType reportType,
        List<Long> areaIds
) implements LinePostbackCommand {

    /**
     * 대상 목록을 불변으로 보존한다.
     */
    public LineReportGenerateCommand {
        areaIds = List.copyOf(areaIds);
    }
}
