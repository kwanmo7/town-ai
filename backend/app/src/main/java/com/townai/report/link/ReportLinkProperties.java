package com.townai.report.link;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LINE에서 Report를 열 때 사용하는 만료 서명 URL 설정이다.
 *
 * @param signingSecret URL 위변조를 검증할 HMAC Secret
 * @param validity 생성한 링크의 유효 기간
 */
@ConfigurationProperties(prefix = "town-ai.report-link")
public record ReportLinkProperties(
        String signingSecret,
        Duration validity
) {
}
