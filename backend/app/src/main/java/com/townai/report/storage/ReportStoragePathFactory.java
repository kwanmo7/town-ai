package com.townai.report.storage;

import com.townai.area.entity.AreaEntity;
import com.townai.report.entity.ReportType;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Report 유형, 대상 Area, 생성일과 ID로 충돌 없는 Storage 객체 경로를 생성한다.
 *
 * <p>경로 형식은 {@code reports/v1/{reportType}/{filename}.md}이다. ID가 항상
 * 파일명에 포함되므로 같은 날 같은 유형과 Area로 여러 번 생성해도 충돌하지 않는다.</p>
 */
@Component
public class ReportStoragePathFactory {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final Clock clock;

    /**
     * Storage 경로 생성기를 만든다.
     *
     * @param clock 파일명에 포함할 현재 날짜의 기준
     */
    public ReportStoragePathFactory(Clock clock) {
        this.clock = clock;
    }

    /**
     * Report 유형에 맞는 파일명과 논리 객체 경로를 만든다.
     *
     * @param reportType 경로 Prefix를 결정할 Report 유형
     * @param reportId 파일 충돌 방지와 DB 추적에 사용할 Report ID
     * @param targetAreas AREA·COMPARE 파일명에 사용할 대상 Area. 표시 순서
     * @return DB와 ReportStorage에 전달할 논리 객체 경로
     */
    public String create(
            ReportType reportType,
            Long reportId,
            List<AreaEntity> targetAreas
    ) {
        String date = LocalDate.now(clock).format(DATE_FORMAT);
        String baseName = switch (reportType) {
            case SUMMARY, ALL -> date + "_" + reportId;
            case AREA, COMPARE -> targetAreas.stream()
                    .map(AreaEntity::getName)
                    .map(this::sanitize)
                    .collect(Collectors.joining("-"))
                    + "_" + date + "_" + reportId;
        };
        return "reports/v1/" + reportType.pathName() + "/" + baseName + ".md";
    }

    /**
     * Area 이름에서 파일시스템 예약 문자와 제어 문자를 제거한다.
     */
    private String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "area";
        }
        String sanitized = value.strip()
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-|-$", "");
        return sanitized.isBlank() ? "area" : sanitized;
    }
}
