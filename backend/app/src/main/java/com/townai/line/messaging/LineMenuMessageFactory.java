package com.townai.line.messaging;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.townai.line.messaging.LineFlexComponents.box;
import static com.townai.line.messaging.LineFlexComponents.button;
import static com.townai.line.messaging.LineFlexComponents.postback;
import static com.townai.line.messaging.LineFlexComponents.separator;
import static com.townai.line.messaging.LineFlexComponents.text;

/**
 * LINE Bot의 고정 메뉴와 Visit 입력 안내를 Flex Message로 생성한다.
 *
 * <p>Area와 Report처럼 DB 값이 필요한 화면은 별도 Factory에서 담당한다. 이
 * Factory는 Rich Menu Postback 이후 즉시 표시할 수 있는 정적 화면만 관리한다.</p>
 */
@Component
public class LineMenuMessageFactory {

    private static final String VISIT_COLOR = "#176B5B";
    private static final String VISIT_LIGHT_COLOR = "#D8F3EA";
    private static final String REPORT_COLOR = "#294C74";
    private static final String REPORT_LIGHT_COLOR = "#DCE9F7";

    /**
     * LINE 메뉴 Message Factory를 생성한다.
     */
    public LineMenuMessageFactory() {
    }

    /**
     * 방문 기록 등록과 리포트 조회를 선택하는 메인 메뉴를 생성한다.
     *
     * @param lineUserId 메시지를 받을 허용 사용자 ID
     * @return LINE Push Message API 요청
     */
    public LinePushRequest createMainMenu(String lineUserId) {
        Map<String, Object> header = header(
                "Town AI",
                "사용할 기능을 선택해주세요.",
                VISIT_COLOR,
                VISIT_LIGHT_COLOR
        );
        Map<String, Object> body = box("vertical", List.of(
                descriptionCard(
                        "방문 기록 등록",
                        "방문한 지역의 점수와 메모를 자연어로 등록합니다.",
                        "#F0F7F5",
                        VISIT_COLOR
                ),
                descriptionCard(
                        "리포트 조회",
                        "등록된 지역을 선택해 분석 리포트를 생성합니다.",
                        "#F5F5F5",
                        "#333333"
                )
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> footer = box("vertical", List.of(
                button(
                        "primary",
                        VISIT_COLOR,
                        postback(
                                "방문 기록 등록",
                                "action=menu&target=visit-register",
                                "방문 기록 등록"
                        )
                ),
                button(
                        "secondary",
                        null,
                        postback(
                                "리포트 조회",
                                "action=menu&target=report",
                                "리포트 조회"
                        )
                )
        )).property("spacing", "sm")
                .property("paddingAll", "16px")
                .build();

        return request(
                lineUserId,
                "Town AI 기능을 선택해주세요.",
                bubble(header, body, footer)
        );
    }

    /**
     * 자연어 Visit 입력 항목과 예시를 안내한다.
     *
     * @param lineUserId 메시지를 받을 허용 사용자 ID
     * @return LINE Push Message API 요청
     */
    public LinePushRequest createVisitRegistrationGuide(
            String lineUserId
    ) {
        Map<String, Object> header = header(
                "방문 기록 등록",
                null,
                VISIT_COLOR,
                VISIT_LIGHT_COLOR
        );
        Map<String, Object> body = box("vertical", List.of(
                text("방문한 지역의 평가를 자연어로 보내주세요.")
                        .property("weight", "bold")
                        .property("wrap", true)
                        .build(),
                text(
                        "지역명, 방문일·다섯 가지 점수·메모를 한 번에 입력해주세요. "
                                + "위치를 명확히 알 수 있는 지역은 도도부현과 "
                                + "시구정촌을 자동으로 보완합니다."
                ).property("size", "sm")
                        .property("color", "#555555")
                        .property("wrap", true)
                        .build(),
                separator("sm"),
                text("입력 예시")
                        .property("size", "sm")
                        .property("weight", "bold")
                        .property("color", VISIT_COLOR)
                        .build(),
                text(
                        "센터미나미를 오늘 방문했어. 분위기 8, 생활 인프라 9, "
                                + "청결도 8, 넓은 집 가능성 7, 접근성 8이야. "
                                + "역 주변이 정돈돼 있고 쇼핑하기 편했어."
                ).property("size", "sm")
                        .property("color", "#333333")
                        .property("wrap", true)
                        .build()
        )).property("spacing", "md")
                .property("paddingAll", "20px")
                .build();
        Map<String, Object> footer = singleMenuFooter();

        return request(
                lineUserId,
                "방문 기록 입력 방법을 안내합니다.",
                bubble(header, body, footer)
        );
    }

    /**
     * 지원하는 네 가지 Report Type을 선택하는 메뉴를 생성한다.
     *
     * @param lineUserId 메시지를 받을 허용 사용자 ID
     * @return LINE Push Message API 요청
     */
    public LinePushRequest createReportTypeMenu(String lineUserId) {
        Map<String, Object> header = header(
                "리포트 조회",
                "생성할 리포트 종류를 선택해주세요.",
                REPORT_COLOR,
                REPORT_LIGHT_COLOR
        );
        Map<String, Object> body = box("vertical", List.of(
                reportTypeButton("한 지역 분석", "AREA", true),
                reportTypeButton("지역 비교", "COMPARE", false),
                reportTypeButton("전체 요약", "SUMMARY", false),
                reportTypeButton("전체 상세 분석", "ALL", false)
        )).property("spacing", "sm")
                .property("paddingAll", "16px")
                .build();

        return request(
                lineUserId,
                "생성할 리포트 종류를 선택해주세요.",
                bubble(header, body, singleMenuFooter())
        );
    }

    private Map<String, Object> reportTypeButton(
            String label,
            String reportType,
            boolean primary
    ) {
        return button(
                primary ? "primary" : "secondary",
                primary ? REPORT_COLOR : null,
                postback(
                        label,
                        "action=report-type&reportType=" + reportType,
                        label
                )
        );
    }

    private Map<String, Object> descriptionCard(
            String title,
            String description,
            String backgroundColor,
            String titleColor
    ) {
        return box("vertical", List.of(
                text(title)
                        .property("weight", "bold")
                        .property("color", titleColor)
                        .build(),
                text(description)
                        .property("size", "sm")
                        .property("color", "#555555")
                        .property("wrap", true)
                        .property("margin", "sm")
                        .build()
        )).property("backgroundColor", backgroundColor)
                .property("cornerRadius", "md")
                .property("paddingAll", "16px")
                .build();
    }

    private Map<String, Object> header(
            String title,
            String subtitle,
            String backgroundColor,
            String subtitleColor
    ) {
        List<Object> contents = new java.util.ArrayList<>();
        contents.add(text(title)
                .property("color", "#FFFFFF")
                .property("size", "xl")
                .property("weight", "bold")
                .build());
        if (subtitle != null) {
            contents.add(text(subtitle)
                    .property("color", subtitleColor)
                    .property("size", "sm")
                    .property("margin", "sm")
                    .property("wrap", true)
                    .build());
        }
        return box("vertical", contents)
                .property("backgroundColor", backgroundColor)
                .property("paddingAll", "20px")
                .build();
    }

    private Map<String, Object> singleMenuFooter() {
        return box("vertical", List.of(
                button(
                        "link",
                        null,
                        postback(
                                "메뉴로 돌아가기",
                                "action=menu&target=main",
                                "메뉴"
                        )
                )
        )).property("paddingAll", "16px")
                .build();
    }

    private Map<String, Object> bubble(
            Map<String, Object> header,
            Map<String, Object> body,
            Map<String, Object> footer
    ) {
        return LineFlexObjectBuilder.type("bubble")
                .property("size", "mega")
                .property("header", header)
                .property("body", body)
                .property("footer", footer)
                .build();
    }

    private LinePushRequest request(
            String lineUserId,
            String altText,
            Map<String, Object> contents
    ) {
        if (lineUserId == null || lineUserId.isBlank()) {
            throw new IllegalArgumentException(
                    "LINE User ID must not be blank."
            );
        }
        return new LinePushRequest(
                lineUserId,
                List.of(LineFlexMessage.of(altText, contents))
        );
    }
}
