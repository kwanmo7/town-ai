package com.townai.line.model;

import java.time.Instant;

/**
 * 서명·사용자·이벤트 유형 검증을 통과해 영속화할 수 있는 최소 LINE 이벤트이다.
 *
 * <p>Text Message와 Postback 중 하나의 Payload만 존재하며 Follow Event는 두 값이
 * 모두 없다. Reply Token과 원본 Webhook Body는 비동기 처리에 필요하지 않으므로
 * 포함하지 않는다.</p>
 *
 * @param webhookEventId 재전송 중에도 유지되는 LINE Webhook Event ID
 * @param lineUserId 허용된 이벤트 발생 사용자 ID
 * @param eventType Backend 내부 이벤트 유형
 * @param messageText Text Message 원문. 다른 이벤트면 {@code null}
 * @param postbackData Postback Data. 다른 이벤트면 {@code null}
 * @param occurredAt LINE Platform에서 이벤트가 발생한 UTC 시각
 */
public record LineWebhookEventPayload(
        String webhookEventId,
        String lineUserId,
        LineWebhookEventType eventType,
        String messageText,
        String postbackData,
        Instant occurredAt
) {
}
