package com.townai.line.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * LINE Messaging API Push 요청에 사용하는 인증과 HTTP 설정이다.
 *
 * @param channelAccessToken Bearer 인증에 사용할 Channel Access Token
 * @param messagingApiBaseUrl LINE Messaging API Base URL
 * @param messagingApiConnectTimeout TCP 연결 제한 시간
 * @param messagingApiReadTimeout 응답 본문 수신 제한 시간
 * @param reportBaseUrl LINE에서 Report를 열 때 사용할 공개 Backend URL
 */
@ConfigurationProperties(prefix = "town-ai.line")
public record LineMessagingProperties(
        String channelAccessToken,
        String messagingApiBaseUrl,
        Duration messagingApiConnectTimeout,
        Duration messagingApiReadTimeout,
        String reportBaseUrl
) {
}
