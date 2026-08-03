package com.townai.line.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * LINE Platform이 Webhook Endpoint로 전달하는 최상위 요청이다.
 *
 * <p>LINE이 향후 필드를 추가해도 기존 이벤트 처리가 깨지지 않도록 모든 단계에서
 * 알 수 없는 필드는 무시한다. 이 DTO는 반드시 원문 Body의 서명 검증이 끝난 뒤에만
 * 역직렬화한다.</p>
 *
 * @param destination Webhook을 수신한 LINE Official Account의 User ID
 * @param events 한 요청에 포함된 Webhook 이벤트 목록
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LineWebhookRequest(
        String destination,
        List<Event> events
) {

    /**
     * 이벤트 목록을 null이 아닌 불변 목록으로 정규화한다.
     */
    public LineWebhookRequest {
        events = events == null ? List.of() : List.copyOf(events);
    }

    /**
     * 지원 여부를 판별하는 데 필요한 Webhook 이벤트 필드이다.
     *
     * @param type LINE 이벤트 유형
     * @param timestamp 이벤트 발생 시각의 Unix Milliseconds
     * @param source 이벤트가 발생한 대화와 사용자
     * @param webhookEventId 재전송에도 유지되는 이벤트 고유 ID
     * @param mode Channel 상태. 실제 처리 대상은 {@code active}
     * @param message Message Event의 메시지 본문
     * @param postback Postback Event의 Data
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Event(
            String type,
            Long timestamp,
            Source source,
            String webhookEventId,
            String mode,
            Message message,
            Postback postback
    ) {
    }

    /**
     * Webhook 이벤트 발생 Source의 최소 정보이다.
     *
     * @param type {@code user}, {@code group} 또는 {@code room}
     * @param userId 이벤트를 발생시킨 LINE User ID
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Source(
            String type,
            String userId
    ) {
    }

    /**
     * V1에서 Text Message 여부를 판별하기 위한 최소 메시지 정보이다.
     *
     * @param type Message 유형
     * @param text Text Message의 원문
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(
            String type,
            String text
    ) {
    }

    /**
     * 메뉴 이동 또는 업무 동작을 전달하는 Postback 정보이다.
     *
     * @param data Backend가 해석할 Postback Data
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Postback(String data) {
    }
}
