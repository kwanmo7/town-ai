package com.townai.line.processing;

import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.entity.LineWebhookEventStatus;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.repository.LineWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * LINE Webhook 이벤트의 점유 Lease와 처리 상태 전환을 관리한다.
 *
 * <p>모든 변경은 Pessimistic Write Lock 안에서 수행한다. 완료·실패 시 현재
 * {@code attemptCount}를 함께 확인해, Lease가 만료된 이전 작업이 더 새로운
 * 처리 시도의 상태를 덮어쓰지 못하게 한다.</p>
 */
@Service
public class LineWebhookEventStateService {

    private static final Duration PROCESSING_LEASE =
            Duration.ofMinutes(3);
    private static final int MAX_ATTEMPTS = 5;
    private static final String MAX_ATTEMPTS_ERROR_CODE =
            "MAX_ATTEMPTS_EXCEEDED";

    private final LineWebhookEventRepository eventRepository;
    private final Clock clock;

    /**
     * 이벤트 상태 관리 Service를 생성한다.
     *
     * @param eventRepository Lock 조회와 상태 저장에 사용할 Repository
     * @param clock Lease 및 처리 시각의 UTC 기준
     */
    public LineWebhookEventStateService(
            LineWebhookEventRepository eventRepository,
            Clock clock
    ) {
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * 처리 가능한 이벤트를 원자적으로 {@code PROCESSING}으로 전환한다.
     *
     * @param webhookEventId 점유할 LINE Webhook Event ID
     * @return 점유 상태와 성공 시 Work Item
     */
    @Transactional
    public LineEventClaim claim(String webhookEventId) {
        Optional<LineWebhookEventEntity> found =
                eventRepository.findByIdForUpdate(webhookEventId);
        if (found.isEmpty()) {
            return LineEventClaim.withoutWork(
                    LineEventClaimStatus.MISSING
            );
        }

        LineWebhookEventEntity event = found.get();
        if (isTerminal(event)) {
            return LineEventClaim.withoutWork(
                    LineEventClaimStatus.TERMINAL
            );
        }

        Instant now = clock.instant();
        if (hasActiveLease(event, now)) {
            return LineEventClaim.withoutWork(
                    LineEventClaimStatus.BUSY
            );
        }
        if (event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.fail(MAX_ATTEMPTS_ERROR_CODE, now);
            return LineEventClaim.withoutWork(
                    LineEventClaimStatus.TERMINAL
            );
        }

        event.startProcessing(now);
        return LineEventClaim.acquired(toWorkItem(event));
    }

    /**
     * 현재 처리 시도가 성공한 경우 이벤트를 완료 상태로 전환한다.
     *
     * @param webhookEventId 처리한 이벤트 ID
     * @param attemptCount Work Item에 기록된 처리 시도 번호
     * @return 현재 시도의 상태가 반영됐으면 {@code true}
     */
    @Transactional
    public boolean complete(
            String webhookEventId,
            int attemptCount
    ) {
        Optional<LineWebhookEventEntity> found =
                eventRepository.findByIdForUpdate(webhookEventId);
        if (found.isEmpty()
                || !isCurrentAttempt(found.get(), attemptCount)) {
            return false;
        }
        found.get().complete(clock.instant());
        return true;
    }

    /**
     * 현재 처리 시도의 실패를 재시도 또는 최종 실패 상태로 기록한다.
     *
     * @param webhookEventId 처리한 이벤트 ID
     * @param attemptCount Work Item에 기록된 처리 시도 번호
     * @param errorCode 민감정보를 포함하지 않는 최대 50자 오류 코드
     * @param retryable 같은 입력으로 다시 시도할 가치가 있는지 여부
     * @return Task 재시도 여부 또는 만료된 이전 시도 여부
     */
    @Transactional
    public LineEventFailureResult fail(
            String webhookEventId,
            int attemptCount,
            String errorCode,
            boolean retryable
    ) {
        Optional<LineWebhookEventEntity> found =
                eventRepository.findByIdForUpdate(webhookEventId);
        if (found.isEmpty()
                || !isCurrentAttempt(found.get(), attemptCount)) {
            return LineEventFailureResult.STALE;
        }

        LineWebhookEventEntity event = found.get();
        String validatedErrorCode = validateErrorCode(errorCode);
        if (!retryable || event.getAttemptCount() >= MAX_ATTEMPTS) {
            event.fail(validatedErrorCode, clock.instant());
            return LineEventFailureResult.TERMINAL;
        }

        event.releaseForRetry(validatedErrorCode);
        return LineEventFailureResult.RETRY;
    }

    private boolean isTerminal(LineWebhookEventEntity event) {
        return event.getStatus() == LineWebhookEventStatus.COMPLETED
                || event.getStatus() == LineWebhookEventStatus.FAILED;
    }

    private boolean hasActiveLease(
            LineWebhookEventEntity event,
            Instant now
    ) {
        if (event.getStatus() != LineWebhookEventStatus.PROCESSING
                || event.getProcessingStartedAt() == null) {
            return false;
        }
        Instant leaseExpiresAt = event.getProcessingStartedAt()
                .plus(PROCESSING_LEASE);
        return leaseExpiresAt.isAfter(now);
    }

    private boolean isCurrentAttempt(
            LineWebhookEventEntity event,
            int attemptCount
    ) {
        return event.getStatus() == LineWebhookEventStatus.PROCESSING
                && event.getAttemptCount() == attemptCount;
    }

    private LineWebhookEventWorkItem toWorkItem(
            LineWebhookEventEntity event
    ) {
        return new LineWebhookEventWorkItem(
                event.getWebhookEventId(),
                event.getLineUserId(),
                event.getEventType(),
                event.getMessageText(),
                event.getPostbackData(),
                event.getOccurredAt(),
                event.getAttemptCount()
        );
    }

    private String validateErrorCode(String errorCode) {
        if (errorCode == null
                || errorCode.isBlank()
                || errorCode.length() > 50) {
            throw new IllegalArgumentException(
                    "LINE event error code must be 1 to 50 characters."
            );
        }
        return errorCode;
    }
}
