package com.townai.line.model;

import java.time.LocalDate;

/**
 * LINE Report 대상 선택 화면에 표시할 Area 요약이다.
 *
 * @param areaId Area ID
 * @param name 지역명
 * @param prefecture 도도부현
 * @param city 시구정촌
 * @param visitCount 등록된 Visit 수
 * @param latestVisitDate 최근 방문일. Visit이 없으면 {@code null}
 */
public record LineReportAreaOption(
        long areaId,
        String name,
        String prefecture,
        String city,
        long visitCount,
        LocalDate latestVisitDate
) {
}
