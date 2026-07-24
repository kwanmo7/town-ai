package com.townai.common.openai;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * OpenAI Responses API 호출에 공통으로 사용하는 설정이다.
 *
 * <p>API Key는 비어 있어도 애플리케이션이 시작되며, 실제 AI 기능을 호출할 때
 * {@code OPENAI_API_ERROR}로 안전하게 실패한다. 나머지 값은 HTTP Client 생성에
 * 필요하므로 {@link OpenAiResponsesClient}가 시작 시 검증한다.</p>
 *
 * @param apiKey Bearer 인증에 사용할 API Key
 * @param baseUrl OpenAI 호환 Responses API의 Base URL
 * @param reportModel Report와 Visit Parser가 공통으로 사용할 모델
 * @param connectTimeout TCP 연결 제한 시간
 * @param readTimeout 모델 응답 본문 수신 제한 시간
 */
@ConfigurationProperties(prefix = "town-ai.openai")
public record OpenAiProperties(
        String apiKey,
        String baseUrl,
        String reportModel,
        Duration connectTimeout,
        Duration readTimeout
) {
}
