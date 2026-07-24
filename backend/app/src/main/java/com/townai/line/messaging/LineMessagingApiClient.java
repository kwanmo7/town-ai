package com.townai.line.messaging;

import com.townai.line.config.LineMessagingProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;

/**
 * LINE Messaging API의 Push Message Endpoint를 호출한다.
 */
@Component
public class LineMessagingApiClient implements LinePushClient {

    private static final String PUSH_PATH = "/v2/bot/message/push";
    private static final String RETRY_KEY_HEADER = "X-Line-Retry-Key";
    private static final String ACCEPTED_REQUEST_ID_HEADER =
            "X-Line-Accepted-Request-Id";
    private static final String TEMPORARY_ERROR =
            "LINE_PUSH_TEMPORARY_ERROR";
    private static final String REJECTED_ERROR =
            "LINE_PUSH_REJECTED";

    private final RestClient restClient;
    private final String channelAccessToken;

    /**
     * Timeout과 Base URL이 적용된 LINE Messaging API Client를 생성한다.
     *
     * @param restClientBuilder Spring RestClient Builder
     * @param properties Channel Access Token과 HTTP 설정
     */
    public LineMessagingApiClient(
            RestClient.Builder restClientBuilder,
            LineMessagingProperties properties
    ) {
        Duration connectTimeout = requireDuration(
                properties.messagingApiConnectTimeout(),
                "LINE Messaging API connect timeout"
        );
        Duration readTimeout = requireDuration(
                properties.messagingApiReadTimeout(),
                "LINE Messaging API read timeout"
        );
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();
        JdkClientHttpRequestFactory requestFactory =
                new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = restClientBuilder
                .baseUrl(requireNonBlank(
                        properties.messagingApiBaseUrl(),
                        "LINE Messaging API base URL"
                ))
                .requestFactory(requestFactory)
                .build();
        this.channelAccessToken = properties.channelAccessToken();
    }

    /**
     * Channel Access Token 설정 여부를 반환한다.
     *
     * @return 비어 있지 않은 Token이 있으면 {@code true}
     */
    @Override
    public boolean isConfigured() {
        return channelAccessToken != null
                && !channelAccessToken.isBlank();
    }

    /**
     * Push Message 요청이 LINE에 수락됐는지 HTTP 상태로 판별한다.
     *
     * @param request 수신자와 Message Object
     * @param retryKey 최초 요청부터 사용할 결정적 UUID
     */
    @Override
    public void push(LinePushRequest request, UUID retryKey) {
        if (!isConfigured()) {
            throw new LineMessagingException(
                    "LINE_CHANNEL_ACCESS_TOKEN_MISSING",
                    true,
                    null
            );
        }

        try {
            restClient.post()
                    .uri(PUSH_PATH)
                    .headers(headers -> {
                        headers.setBearerAuth(channelAccessToken);
                        headers.set(
                                RETRY_KEY_HEADER,
                                retryKey.toString()
                        );
                    })
                    .body(request)
                    .exchange((httpRequest, response) -> {
                        validateResponse(
                                response.getStatusCode(),
                                response.getHeaders().getFirst(
                                        ACCEPTED_REQUEST_ID_HEADER
                                )
                        );
                        return null;
                    });
        } catch (LineMessagingException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LineMessagingException(
                    TEMPORARY_ERROR,
                    true,
                    exception
            );
        }
    }

    private void validateResponse(
            HttpStatusCode status,
            String acceptedRequestId
    ) {
        if (status.is2xxSuccessful()) {
            return;
        }
        if (status.value() == 409
                && acceptedRequestId != null
                && !acceptedRequestId.isBlank()) {
            return;
        }

        boolean retryable = status.is5xxServerError()
                || status.value() == 408
                || status.value() == 429;
        throw new LineMessagingException(
                retryable ? TEMPORARY_ERROR : REJECTED_ERROR,
                retryable,
                null
        );
    }

    private String requireNonBlank(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " is required.");
        }
        return value;
    }

    private Duration requireDuration(
            Duration value,
            String settingName
    ) {
        if (value == null || value.isNegative() || value.isZero()) {
            throw new IllegalStateException(
                    settingName + " must be positive."
            );
        }
        return value;
    }
}
