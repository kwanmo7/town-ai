package com.townai.line.model;

import java.time.Instant;

/**
 * 하나의 처리 시도가 점유한 LINE Webhook 이벤트 Snapshot이다.
 *
 * @param webhookEventId LINE Webhook Event ID
 * @param lineUserId 이벤트를 발생시킨 허용 사용자 ID
 * @param eventType 내부 이벤트 유형
 * @param messageText Text Message 원문
 * @param postbackData 확인·취소 Postback Data
 * @param occurredAt LINE Platform 이벤트 발생 시각
 * @param attemptCount 현재 처리 시도 번호
 */
public record LineWebhookEventWorkItem(
        String webhookEventId,
        String lineUserId,
        LineWebhookEventType eventType,
        String messageText,
        String postbackData,
        Instant occurredAt,
        int attemptCount
) {
}
