package com.townai.line.entity;

import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * LINE Webhook 이벤트의 최소 Payload와 비동기 처리 상태를 저장하는 Entity이다.
 *
 * <p>{@code webhookEventId}를 PK로 사용해 LINE의 재전송과 동일 요청 안의 중복
 * 이벤트가 별도 Row를 만들지 못하게 한다. 원문 Webhook Body, Reply Token 및
 * Channel Access Token은 저장하지 않는다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineWebhookEventEntity {

    private String webhookEventId;

    private String lineUserId;

    private LineWebhookEventType eventType;

    private String messageText;

    private String postbackData;

    private LineWebhookEventStatus status;

    private int attemptCount;

    private String lastErrorCode;

    private Long revisionSourceDraftId;

    private Instant occurredAt;

    private Instant processingStartedAt;

    private Instant processedAt;

    private Instant createdAt;

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

    public static LineWebhookEventEntity restore(
            String webhookEventId,
            String lineUserId,
            LineWebhookEventType eventType,
            String messageText,
            String postbackData,
            LineWebhookEventStatus status,
            int attemptCount,
            String lastErrorCode,
            Long revisionSourceDraftId,
            Instant occurredAt,
            Instant processingStartedAt,
            Instant processedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        // 재시도 판단에 필요한 처리 상태와 Lease 시각을 Firestore 문서에서 모두 복원한다.
        LineWebhookEventEntity event = LineWebhookEventEntity.builder()
                .webhookEventId(webhookEventId)
                .lineUserId(lineUserId)
                .eventType(eventType)
                .messageText(messageText)
                .postbackData(postbackData)
                .status(status)
                .attemptCount(attemptCount)
                .occurredAt(occurredAt)
                .build();
        event.lastErrorCode = lastErrorCode;
        event.revisionSourceDraftId = revisionSourceDraftId;
        event.processingStartedAt = processingStartedAt;
        event.processedAt = processedAt;
        event.createdAt = createdAt;
        event.updatedAt = updatedAt;
        return event;
    }

    /**
     * Webhook Event 상태 저장 결과의 Audit 시각을 반영한다.
     *
     * @param persistedAt 저장이 완료된 UTC 시각
     */
    public void markPersisted(Instant persistedAt) {
        // 동일 Event 상태 전환에서는 최초 생성 시각을 유지한다.
        if (createdAt == null) {
            createdAt = persistedAt;
        }
        updatedAt = persistedAt;
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

    /**
     * Text Message가 최초 처리에서 선택한 수정 원본을 보존한다.
     *
     * <p>Task 재시도 시 원본 상태가 바뀌었더라도 일반 신규 방문 입력으로 해석되지
     * 않도록 수정 의도를 이벤트에 고정한다.</p>
     *
     * @param draftId 수정 대상으로 선택한 원본 Draft ID
     */
    public void assignRevisionSource(Long draftId) {
        if (draftId == null || draftId <= 0) {
            throw new IllegalArgumentException(
                    "Revision source Draft ID must be positive."
            );
        }
        if (revisionSourceDraftId != null
                && !revisionSourceDraftId.equals(draftId)) {
            throw new IllegalStateException(
                    "Revision source Draft cannot be changed."
            );
        }
        this.revisionSourceDraftId = draftId;
    }

    private Instant toSeconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
