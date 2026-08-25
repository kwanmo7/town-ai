package com.townai.report.link;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReportSignedLinkServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-26T01:00:00Z");
    private ReportSignedLinkService service;

    @BeforeEach
    void setUp() {
        service = new ReportSignedLinkService(
                new ReportLinkProperties(
                        "test-signing-secret-that-is-at-least-32-characters",
                        Duration.ofDays(30)
                ),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
    }

    @Test
    void createsAndVerifiesContentLink() {
        URI uri = URI.create(service.createPath(10L, ReportLinkAction.CONTENT));
        Map<String, String> query = query(uri);

        assertThat(uri.getPath()).isEqualTo("/api/public/reports/10/content");
        assertThat(query.get("expires")).isEqualTo("1790298000");
        assertThat(query.get("signature")).isNotBlank();

        service.verify(
                10L,
                ReportLinkAction.CONTENT,
                Long.parseLong(query.get("expires")),
                query.get("signature")
        );
    }

    @Test
    void rejectsSignatureForDifferentActionOrReport() {
        URI uri = URI.create(service.createPath(10L, ReportLinkAction.CONTENT));
        Map<String, String> query = query(uri);
        long expires = Long.parseLong(query.get("expires"));
        String signature = query.get("signature");

        assertInvalid(() -> service.verify(
                10L,
                ReportLinkAction.DOWNLOAD,
                expires,
                signature
        ));
        assertInvalid(() -> service.verify(
                11L,
                ReportLinkAction.CONTENT,
                expires,
                signature
        ));
    }

    @Test
    void rejectsExpiredLink() {
        ReportSignedLinkService laterService = new ReportSignedLinkService(
                new ReportLinkProperties(
                        "test-signing-secret-that-is-at-least-32-characters",
                        Duration.ofDays(30)
                ),
                Clock.fixed(NOW.plus(Duration.ofDays(31)), ZoneOffset.UTC)
        );
        URI uri = URI.create(service.createPath(10L, ReportLinkAction.CONTENT));
        Map<String, String> query = query(uri);

        assertThatThrownBy(() -> laterService.verify(
                10L,
                ReportLinkAction.CONTENT,
                Long.parseLong(query.get("expires")),
                query.get("signature")
        )).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.REPORT_LINK_EXPIRED)
        );
    }

    @Test
    void rejectsMissingSigningSecretWhenCreatingLink() {
        ReportSignedLinkService invalidService = new ReportSignedLinkService(
                new ReportLinkProperties("", Duration.ofDays(30)),
                Clock.fixed(NOW, ZoneOffset.UTC)
        );

        assertThatThrownBy(() -> invalidService.createPath(10L, ReportLinkAction.CONTENT))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("REPORT_LINK_SIGNING_SECRET");
    }

    private void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOfSatisfying(ApiException.class, exception ->
                assertThat(exception.errorCode()).isEqualTo(ErrorCode.INVALID_REPORT_LINK)
        );
    }

    private Map<String, String> query(URI uri) {
        return Arrays.stream(uri.getQuery().split("&"))
                .map(value -> value.split("=", 2))
                .collect(Collectors.toMap(value -> value[0], value -> value[1]));
    }
}
