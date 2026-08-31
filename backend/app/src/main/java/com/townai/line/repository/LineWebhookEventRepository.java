package com.townai.line.repository;

import com.townai.line.entity.LineWebhookEventEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** LINE Webhook Event의 처리 상태와 멱등성 기록을 관리하는 영속성 경계이다. */
public interface LineWebhookEventRepository {

    /**
     * Webhook Event의 현재 처리 상태를 저장한다.
     *
     * @param event 저장할 Event
     * @return 저장 시각이 반영된 Event
     */
    LineWebhookEventEntity save(LineWebhookEventEntity event);

    /**
     * Webhook Event 여러 건의 상태를 같은 작업 흐름에서 저장한다.
     *
     * @param events 저장할 Event 목록
     * @return 저장된 Event 목록
     */
    List<LineWebhookEventEntity> saveAll(List<LineWebhookEventEntity> events);

    /**
     * Webhook Event ID로 처리 상태를 조회한다.
     *
     * @param webhookEventId LINE Webhook Event ID
     * @return 존재하는 Event
     */
    Optional<LineWebhookEventEntity> findById(String webhookEventId);

    /**
     * 같은 Event를 상태 전환하는 Transaction에서 사용할 문서를 조회한다.
     *
     * @param webhookEventId LINE Webhook Event ID
     * @return 존재하는 Event
     */
    default Optional<LineWebhookEventEntity> findByIdForUpdate(
            String webhookEventId
    ) {
        return findById(webhookEventId);
    }

    /**
     * 여러 Webhook Event ID에 해당하는 상태를 조회한다.
     *
     * @param ids Event ID 목록
     * @return 존재하는 Event 목록
     */
    List<LineWebhookEventEntity> findAllById(Collection<String> ids);

    /**
     * Draft가 참조하지 않는 오래된 완료·실패 Event만 삭제한다.
     *
     * @param cutoff 보존 기준 시각
     * @return 삭제한 Event 수
     */
    int deleteTerminalCreatedBeforeWithoutDraft(Instant cutoff);
}
