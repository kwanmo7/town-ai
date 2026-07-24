package com.townai.line.repository;

import com.townai.line.entity.LineWebhookEventEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * LINE Webhook 이벤트의 영속화와 상태 조회를 담당한다.
 */
public interface LineWebhookEventRepository
        extends JpaRepository<LineWebhookEventEntity, String> {

    /**
     * 상태 전환 동안 같은 이벤트를 다른 Transaction이 수정하지 못하게 잠근다.
     *
     * @param webhookEventId 점유하거나 상태를 변경할 이벤트 ID
     * @return Write Lock이 적용된 이벤트
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT event
            FROM LineWebhookEventEntity event
            WHERE event.webhookEventId = :webhookEventId
            """)
    Optional<LineWebhookEventEntity> findByIdForUpdate(
            @Param("webhookEventId") String webhookEventId
    );

    /**
     * 보존 기간을 지났고 Draft가 참조하지 않는 완료·실패 이벤트를 삭제한다.
     *
     * <p>{@code RECEIVED}와 {@code PROCESSING}은 유실 또는 Lease 복구 점검 대상이므로
     * 자동 정리하지 않는다.</p>
     *
     * @param cutoff 생성 시각의 제외 상한
     * @return 삭제한 Webhook Event Row 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE target_event
            FROM line_webhook_event target_event
            WHERE target_event.created_at < :cutoff
              AND target_event.status IN ('COMPLETED', 'FAILED')
              AND NOT EXISTS (
                  SELECT 1
                  FROM line_visit_draft draft
                  WHERE draft.source_webhook_event_id =
                        target_event.webhook_event_id
              )
            """, nativeQuery = true)
    int deleteTerminalCreatedBeforeWithoutDraft(
            @Param("cutoff") Instant cutoff
    );
}
