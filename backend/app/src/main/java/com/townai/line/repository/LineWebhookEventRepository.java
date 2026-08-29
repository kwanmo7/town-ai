package com.townai.line.repository;

import com.townai.line.entity.LineWebhookEventEntity;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for LINE Webhook Event documents. */
public interface LineWebhookEventRepository {

    LineWebhookEventEntity save(LineWebhookEventEntity event);

    List<LineWebhookEventEntity> saveAll(List<LineWebhookEventEntity> events);

    Optional<LineWebhookEventEntity> findById(String webhookEventId);

    default Optional<LineWebhookEventEntity> findByIdForUpdate(
            String webhookEventId
    ) {
        return findById(webhookEventId);
    }

    List<LineWebhookEventEntity> findAllById(Collection<String> ids);

    int deleteTerminalCreatedBeforeWithoutDraft(Instant cutoff);
}
