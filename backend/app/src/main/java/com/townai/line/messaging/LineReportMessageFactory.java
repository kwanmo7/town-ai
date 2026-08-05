package com.townai.line.messaging;

import com.townai.line.config.LineMessagingProperties;
import com.townai.line.model.LineReportAreaOption;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.townai.line.messaging.LineFlexComponents.box;
import static com.townai.line.messaging.LineFlexComponents.button;
import static com.townai.line.messaging.LineFlexComponents.postback;
import static com.townai.line.messaging.LineFlexComponents.separator;
import static com.townai.line.messaging.LineFlexComponents.text;
import static com.townai.line.messaging.LineFlexComponents.uri;

/**
 * LINE Report 대상 선택·생성 상태·완료 화면을 Flex Message로 생성한다.
 */
@Component
public class LineReportMessageFactory {

    private static final String REPORT_COLOR = "#294C74";
    private final String reportBaseUrl;
    private final ZoneId userTimeZone;

    /**
     * Report 링크와 사용자 날짜 표시 설정을 적용한다.
     *
     * @param properties LINE Messaging과 Report 공개 URL 설정
     * @param userTimeZone 생성일을 표시할 사용자 시간대
     */
    public LineReportMessageFactory(
            LineMessagingProperties properties,
            ZoneId userTimeZone
    ) {
        this.reportBaseUrl = normalizeBaseUrl(properties.reportBaseUrl());
        this.userTimeZone = userTimeZone;
    }

    /**
     * AREA Report 대상 목록을 Carousel로 생성한다.
     *
     * @param lineUserId 수신 사용자
     * @param options Visit이 존재하는 Area 목록
     * @return 대상 선택 메시지
     */
    public LinePushRequest createAreaSelection(
            String lineUserId,
            List<LineReportAreaOption> options
    ) {
        if (options.isEmpty()) {
            return createUnavailable(lineUserId, "분석 가능한 방문 기록이 없습니다.");
        }
        List<Object> bubbles = options.stream()
                .map(this::areaBubble)
                .map(value -> (Object) value)
                .toList();
        Map<String, Object> carousel = LineFlexObjectBuilder.type("carousel")
                .property("contents", bubbles)
                .build();
        return request(
                lineUserId,
                "분석할 지역을 선택해주세요.",
                carousel
        );
    }

    /**
     * COMPARE 대상 선택 상태를 생성한다.
     *
     * @param lineUserId 수신 사용자
     * @param options 선택 가능한 Area 목록
     * @param selectedAreaIds 현재 선택 목록
     * @param warning 선택 제한 안내. 없으면 {@code null}
     * @return 비교 대상 선택 메시지
     */
    public LinePushRequest createCompareSelection(
            String lineUserId,
            List<LineReportAreaOption> options,
            List<Long> selectedAreaIds,
            String warning
    ) {
        if (options.size() < 2) {
            return createUnavailable(
                    lineUserId,
                    "비교하려면 방문 기록이 있는 지역이 2개 이상 필요합니다."
            );
        }
        String selectedValue = selectedAreaIds.stream()
                .map(String::valueOf)
                .collect(java.util.stream.Collectors.joining(","));
        List<Object> contents = new ArrayList<>();
        contents.add(text("비교할 지역을 2~5개 선택해주세요.")
                .property("wrap", true)
                .build());
        contents.add(text("현재 " + selectedAreaIds.size() + "개 선택")
                .property("size", "sm")
                .property("color", "#777777")
                .build());
        if (warning != null) {
            contents.add(text(warning)
                    .property("size", "sm")
                    .property("color", "#A66A00")
                    .property("wrap", true)
                    .build());
        }
        for (LineReportAreaOption option : options) {
            boolean selected = selectedAreaIds.contains(option.areaId());
            String data = "action=compare-toggle&areaId=" + option.areaId();
            if (!selectedValue.isEmpty()) {
                data += "&selectedAreaIds=" + selectedValue;
            }
            contents.add(button(
                    selected ? "primary" : "secondary",
                    selected ? REPORT_COLOR : null,
                    postback(
                            (selected ? "✓ " : "") + option.name(),
                            data,
                            option.name() + (selected ? " 선택 해제" : " 선택")
                    )
            ));
        }
        List<Object> footerContents = new ArrayList<>();
        if (selectedAreaIds.size() >= 2) {
            footerContents.add(button(
                    "primary",
                    REPORT_COLOR,
                    postback(
                            "비교 리포트 생성",
                            "action=report-generate&reportType=COMPARE&areaIds="
                                    + selectedValue,
                            "비교 리포트 생성"
                    )
            ));
        }
        footerContents.add(menuButton("리포트 유형으로 돌아가기", "report"));

        return request(
                lineUserId,
                "비교할 지역을 선택해주세요.",
                bubble(
                        header("지역 비교"),
                        box("vertical", contents)
                                .property("spacing", "sm")
                                .property("paddingAll", "20px")
                                .build(),
                        box("vertical", footerContents)
                                .property("spacing", "sm")
                                .property("paddingAll", "16px")
                                .build()
                )
        );
    }

    /**
     * AI Report 생성이 진행 중임을 안내한다.
     *
     * @param lineUserId 수신 사용자
     * @param reportType 생성 유형
     * @return 생성 안내 메시지
     */
    public LinePushRequest createGenerating(
            String lineUserId,
            ReportType reportType
    ) {
        Map<String, Object> body = box("vertical", List.of(
                text(reportTypeLabel(reportType) + "을 생성하고 있습니다.")
                        .property("weight", "bold")
                        .property("wrap", true)
                        .build(),
                text("완료되면 이 채팅으로 결과를 보내드립니다.")
                        .property("size", "sm")
                        .property("color", "#777777")
                        .property("wrap", true)
                        .build()
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        return request(
                lineUserId,
                "리포트를 생성하고 있습니다.",
                bubble(header("리포트 생성 중"), body, null)
        );
    }

    /**
     * 생성된 Report의 조회·다운로드 링크를 제공한다.
     *
     * @param lineUserId 수신 사용자
     * @param report 생성된 Report
     * @param targetLabel 분석 대상 표시 문구
     * @return Report 완료 메시지
     */
    public LinePushRequest createResult(
            String lineUserId,
            ReportResponse report,
            String targetLabel
    ) {
        return createResultCard(
                lineUserId,
                report,
                targetLabel,
                "리포트 생성 완료",
                "리포트 생성이 완료되었습니다."
        );
    }

    /**
     * 현재 입력과 같은 기존 Report를 새 AI 호출 없이 안내한다.
     *
     * @param lineUserId 수신 사용자
     * @param report 재사용하는 기존 Report
     * @param targetLabel 분석 대상 표시 문구
     * @return 기존 Report 조회 메시지
     */
    public LinePushRequest createReusableResult(
            String lineUserId,
            ReportResponse report,
            String targetLabel
    ) {
        return createResultCard(
                lineUserId,
                report,
                targetLabel,
                "기존 리포트",
                "기존 리포트를 불러왔습니다."
        );
    }

    private LinePushRequest createResultCard(
            String lineUserId,
            ReportResponse report,
            String targetLabel,
            String title,
            String altText
    ) {
        String reportPath = "/api/reports/" + report.id();
        Map<String, Object> body = box("vertical", List.of(
                keyValue("종류", reportTypeLabel(report.reportType())),
                keyValue("대상", targetLabel),
                keyValue(
                        "생성일",
                        report.createdAt()
                                .atZone(userTimeZone)
                                .toLocalDate()
                                .toString()
                )
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> footer = box("vertical", List.of(
                button(
                        "primary",
                        REPORT_COLOR,
                        uri("리포트 보기", reportBaseUrl + reportPath + "/content")
                ),
                button(
                        "secondary",
                        null,
                        uri("Markdown 다운로드", reportBaseUrl + reportPath + "/download")
                ),
                menuButton("다른 리포트 조회", "report"),
                menuButton("메인 메뉴", "main")
        )).property("spacing", "sm")
                .property("paddingAll", "16px")
                .build();
        return request(
                lineUserId,
                altText,
                bubble(header(title), body, footer)
        );
    }

    private Map<String, Object> areaBubble(LineReportAreaOption option) {
        Map<String, Object> areaHeader = box("vertical", List.of(
                text(option.name())
                        .property("color", "#FFFFFF")
                        .property("size", "xl")
                        .property("weight", "bold")
                        .property("wrap", true)
                        .build(),
                text(option.prefecture() + " " + option.city())
                        .property("color", "#DCE9F7")
                        .property("size", "sm")
                        .property("wrap", true)
                        .build()
        )).property("backgroundColor", REPORT_COLOR)
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> body = box("vertical", List.of(
                keyValue("방문 기록", option.visitCount() + "건"),
                keyValue("최근 방문일", option.latestVisitDate().toString())
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> footer = box("vertical", List.of(
                button(
                        "primary",
                        REPORT_COLOR,
                        postback(
                                "이 지역 분석",
                                "action=report-generate&reportType=AREA&areaId="
                                        + option.areaId(),
                                option.name() + " 지역 분석"
                        )
                )
        )).property("paddingAll", "16px").build();
        return bubble(areaHeader, body, footer);
    }

    /**
     * Report 생성 전에 데이터가 부족함을 안내한다.
     *
     * @param lineUserId 수신 사용자
     * @param message 생성할 수 없는 구체적인 이유
     * @return 방문 기록 등록과 메인 메뉴 이동 버튼이 있는 안내 메시지
     */
    public LinePushRequest createUnavailable(
            String lineUserId,
            String message
    ) {
        Map<String, Object> body = box("vertical", List.of(
                text(message).property("wrap", true).build(),
                separator("md"),
                text("먼저 방문 기록을 등록해주세요.")
                        .property("size", "sm")
                        .property("color", "#777777")
                        .build()
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> footer = box("vertical", List.of(
                menuButton("방문 기록 등록", "visit-register"),
                menuButton("메인 메뉴", "main")
        )).property("spacing", "sm")
                .property("paddingAll", "16px")
                .build();
        return request(
                lineUserId,
                "리포트를 생성할 데이터가 부족합니다.",
                bubble(header("리포트 생성 불가"), body, footer)
        );
    }

    private Map<String, Object> header(String title) {
        return box("vertical", List.of(
                text(title)
                        .property("color", "#FFFFFF")
                        .property("size", "xl")
                        .property("weight", "bold")
                        .build()
        )).property("backgroundColor", REPORT_COLOR)
                .property("paddingAll", "20px")
                .build();
    }

    private Map<String, Object> keyValue(String key, String value) {
        return box("horizontal", List.of(
                text(key)
                        .property("color", "#777777")
                        .property("size", "sm")
                        .property("flex", 2)
                        .build(),
                text(value)
                        .property("align", "end")
                        .property("weight", "bold")
                        .property("wrap", true)
                        .property("flex", 4)
                        .build()
        )).build();
    }

    private Map<String, Object> menuButton(String label, String target) {
        return button(
                "secondary",
                null,
                postback(
                        label,
                        "action=menu&target=" + target,
                        label
                )
        );
    }

    private Map<String, Object> bubble(
            Map<String, Object> header,
            Map<String, Object> body,
            Map<String, Object> footer
    ) {
        LineFlexObjectBuilder builder = LineFlexObjectBuilder.type("bubble")
                .property("size", "mega")
                .property("header", header)
                .property("body", body);
        if (footer != null) {
            builder.property("footer", footer);
        }
        return builder.build();
    }

    private LinePushRequest request(
            String lineUserId,
            String altText,
            Map<String, Object> contents
    ) {
        return new LinePushRequest(
                lineUserId,
                List.of(LineFlexMessage.of(altText, contents))
        );
    }

    private String reportTypeLabel(ReportType reportType) {
        return switch (reportType) {
            case AREA -> "한 지역 분석";
            case COMPARE -> "지역 비교";
            case SUMMARY -> "전체 요약";
            case ALL -> "전체 상세 분석";
        };
    }

    private String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("LINE Report base URL is required.");
        }
        URI uri = URI.create(value);
        if (uri.getScheme() == null || uri.getHost() == null) {
            throw new IllegalStateException("LINE Report base URL is invalid.");
        }
        return value.endsWith("/")
                ? value.substring(0, value.length() - 1)
                : value;
    }
}
