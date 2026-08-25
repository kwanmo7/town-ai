package com.townai.line.messaging;

import com.townai.line.config.LineMessagingProperties;
import com.townai.line.model.LineReportAreaOption;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportType;
import com.townai.report.link.ReportLinkProperties;
import com.townai.report.link.ReportSignedLinkService;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineReportMessageFactoryTest {

    private final ObjectMapper objectMapper = JsonMapper.builder().build();
    private final LineReportMessageFactory factory =
            new LineReportMessageFactory(
                    new LineMessagingProperties(
                            "token",
                            "https://api.line.me",
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(2),
                            "https://town-ai.example.com/"
                    ),
                    ZoneId.of("Asia/Tokyo"),
                    new ReportSignedLinkService(
                            new ReportLinkProperties(
                                    "test-signing-secret-that-is-at-least-32-characters",
                                    Duration.ofDays(30)
                            ),
                            Clock.fixed(
                                    Instant.parse("2026-08-03T01:02:03Z"),
                                    ZoneId.of("UTC")
                            )
                    )
            );

    @Test
    void createsDynamicAreaAndCompareSelection() throws JacksonException {
        List<LineReportAreaOption> options = List.of(
                new LineReportAreaOption(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        3,
                        LocalDate.parse("2026-07-28")
                ),
                new LineReportAreaOption(
                        2L,
                        "무사시코스기",
                        "가나가와현",
                        "가와사키시",
                        2,
                        LocalDate.parse("2026-07-20")
                )
        );

        String areaJson = objectMapper.writeValueAsString(
                factory.createAreaSelection("user-1", options)
        );
        String compareJson = objectMapper.writeValueAsString(
                factory.createCompareSelection(
                        "user-1",
                        options,
                        List.of(1L, 2L),
                        null
                )
        );

        assertTrue(areaJson.contains("센터미나미"));
        assertTrue(areaJson.contains("reportType=AREA&amp;areaId=1")
                || areaJson.contains("reportType=AREA&areaId=1"));
        assertTrue(compareJson.contains("✓ 센터미나미"));
        assertTrue(compareJson.contains("areaIds=1,2"));
    }

    @Test
    void createsReportResultWithConfiguredLinks() throws JacksonException {
        ReportResponse report = new ReportResponse(
                10L,
                ReportType.AREA,
                "gpt-test",
                "area-v1",
                Instant.parse("2026-08-03T01:02:03Z")
        );

        String json = objectMapper.writeValueAsString(
                factory.createResult("user-1", report, "센터미나미")
        );

        assertTrue(json.contains(
                "https://town-ai.example.com/api/public/reports/10/content?expires="
        ));
        assertTrue(json.contains(
                "https://town-ai.example.com/api/public/reports/10/download?expires="
        ));
        assertTrue(json.contains("signature="));
        assertTrue(json.contains("action=menu&amp;target=main")
                || json.contains("action=menu&target=main"));
        assertTrue(json.contains("2026-08-03"));
    }

    @Test
    void labelsReusedReportAsExisting() throws JacksonException {
        ReportResponse report = new ReportResponse(
                10L,
                ReportType.ALL,
                "gpt-test",
                "all-v1",
                Instant.parse("2026-08-03T01:02:03Z")
        );

        String json = objectMapper.writeValueAsString(
                factory.createReusableResult(
                        "user-1",
                        report,
                        "전체 지역"
                )
        );

        assertTrue(json.contains("기존 리포트"));
        assertTrue(json.contains("기존 리포트를 불러왔습니다."));
    }

    @Test
    void rejectsEnabledLineMessagingWithoutReportSigningSecret() {
        ReportSignedLinkService invalidSigner = new ReportSignedLinkService(
                new ReportLinkProperties("", Duration.ofDays(30)),
                Clock.fixed(
                        Instant.parse("2026-08-03T01:02:03Z"),
                        ZoneId.of("UTC")
                )
        );

        assertThatThrownBy(() -> new LineReportMessageFactory(
                new LineMessagingProperties(
                        "production-token",
                        "https://api.line.me",
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(2),
                        "https://town-ai.example.com"
                ),
                ZoneId.of("Asia/Tokyo"),
                invalidSigner
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPORT_LINK_SIGNING_SECRET");
    }
}
