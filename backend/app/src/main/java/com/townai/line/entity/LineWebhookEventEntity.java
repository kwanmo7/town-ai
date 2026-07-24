package com.townai.line.entity;

import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * LINE Webhook 이벤트의 최소 Payload와 비동기 처리 상태를 저장하는 Entity이다.
 *
 * <p>{@code webhookEventId}를 PK로 사용해 LINE의 재전송과 동일 요청 안의 중복
 * 이벤트가 별도 Row를 만들지 못하게 한다. 원문 Webhook Body, Reply Token 및
 * Channel Access Token은 저장하지 않는다.</p>
 */
@Entity
@Table(name = "line_webhook_event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineWebhookEventEntity {

    @Id
    @Column(name = "webhook_event_id", length = 64)
    private String webhookEventId;

    @Column(name = "line_user_id", nullable = false, length = 64)
    private String lineUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private LineWebhookEventType eventType;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "postback_data", length = 255)
    private String postbackData;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private LineWebhookEventStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private LineWebhookEventEntity(
            String webhookEventId,
            String lineUserId,
            LineWebhookEventType eventType,
            String messageText,
            String postbackData,
            LineWebhookEventStatus status,
            int attemptCount,
            Instant occurredAt
    ) {
        this.webhookEventId = webhookEventId;
        this.lineUserId = lineUserId;
        this.eventType = eventType;
        this.messageText = messageText;
        this.postbackData = postbackData;
        this.status = status;
        this.attemptCount = attemptCount;
        this.occurredAt = occurredAt;
    }

    /**
     * 검증을 통과한 Webhook Payload를 최초 수신 상태의 Entity로 변환한다.
     *
     * @param payload 서명·사용자·이벤트 유형 검증을 통과한 Payload
     * @return {@link LineWebhookEventStatus#RECEIVED} 상태의 새 Entity
     */
    public static LineWebhookEventEntity received(
            LineWebhookEventPayload payload
    ) {
        return LineWebhookEventEntity.builder()
                .webhookEventId(payload.webhookEventId())
                .lineUserId(payload.lineUserId())
                .eventType(payload.eventType())
                .messageText(payload.messageText())
                .postbackData(payload.postbackData())
                .status(LineWebhookEventStatus.RECEIVED)
                .attemptCount(0)
                .occurredAt(payload.occurredAt().truncatedTo(
                        ChronoUnit.SECONDS
                ))
                .build();
    }

    /**
     * 이벤트를 새 처리 시도가 점유한 상태로 전환한다.
     *
     * @param startedAt 처리 Lease가 시작된 UTC 시각
     */
    public void startProcessing(Instant startedAt) {
        this.status = LineWebhookEventStatus.PROCESSING;
        this.attemptCount++;
        this.processingStartedAt = toSeconds(startedAt);
        this.processedAt = null;
        this.lastErrorCode = null;
    }

    /**
     * 처리 결과와 LINE Push 전송이 성공한 이벤트를 완료 상태로 전환한다.
     *
     * @param completedAt 처리가 완료된 UTC 시각
     */
    public void complete(Instant completedAt) {
        this.status = LineWebhookEventStatus.COMPLETED;
        this.processingStartedAt = null;
        this.processedAt = toSeconds(completedAt);
        this.lastErrorCode = null;
    }

    /**
     * 일시적인 처리 실패를 기록하고 다음 Task가 다시 점유할 수 있게 한다.
     *
     * @param errorCode 민감정보를 포함하지 않는 내부 오류 코드
     */
    public void releaseForRetry(String errorCode) {
        this.status = LineWebhookEventStatus.RECEIVED;
        this.processingStartedAt = null;
        this.processedAt = null;
        this.lastErrorCode = errorCode;
    }

    /**
     * 재시도하지 않을 이벤트를 최종 실패 상태로 전환한다.
     *
     * @param errorCode 민감정보를 포함하지 않는 내부 오류 코드
     * @param failedAt 처리를 종료한 UTC 시각
     */
    public void fail(String errorCode, Instant failedAt) {
        this.status = LineWebhookEventStatus.FAILED;
        this.processingStartedAt = null;
        this.processedAt = toSeconds(failedAt);
        this.lastErrorCode = errorCode;
    }

    private Instant toSeconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
