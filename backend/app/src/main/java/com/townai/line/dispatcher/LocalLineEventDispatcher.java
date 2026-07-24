package com.townai.line.dispatcher;

import com.townai.line.config.LineTaskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Local Backend의 내부 Task Endpoint를 HTTP로 호출하는 Dispatcher이다.
 *
 * <p>Production의 Cloud Tasks와 같은 HTTP 경계를 사용해 Local에서도 Controller,
 * 이벤트 점유 및 재시도 흐름을 검증한다. 내부 Endpoint가 성공 응답을 반환한 경우에만
 * 전달 성공으로 판단한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.line",
        name = "event-dispatcher",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalLineEventDispatcher implements LineEventDispatcher {

    private final RestClient restClient;
    private final String targetUrl;

    /**
     * Local HTTP Dispatcher를 생성한다.
     *
     * @param restClientBuilder Spring이 제공하는 RestClient Builder
     * @param properties Local 내부 Task Endpoint 설정
     */
    public LocalLineEventDispatcher(
            RestClient.Builder restClientBuilder,
            LineTaskProperties properties
    ) {
        this.restClient = restClientBuilder.build();
        this.targetUrl = requireNonBlank(
                properties.localTaskTargetUrl(),
                "LINE local task target URL"
        );
    }

    /**
     * Local 내부 Task Endpoint로 이벤트 ID를 전달한다.
     *
     * @param webhookEventId DB에 저장된 LINE Webhook Event ID
     */
    @Override
    public void dispatch(String webhookEventId) {
        String eventUrl = appendEventId(targetUrl, webhookEventId);
        try {
            restClient.post()
                    .uri(eventUrl)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw new LineEventDispatchException(
                    "Local LINE event dispatch failed.",
                    exception
            );
        }
    }

    private String appendEventId(String baseUrl, String webhookEventId) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment(webhookEventId)
                .build()
                .encode()
                .toUriString();
    }

    private String requireNonBlank(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " is required.");
        }
        return value;
    }
}
