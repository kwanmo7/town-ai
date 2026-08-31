package com.townai.line.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.entity.LineWebhookEventStatus;
import com.townai.line.model.LineWebhookEventType;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** LINE Webhook Event ID를 문서 ID로 사용해 중복 처리를 막는 Firestore 구현이다. */
@Repository
public class FirestoreLineWebhookEventRepository
        implements LineWebhookEventRepository {

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final Clock clock;

    /**
     * LINE Webhook Event Firestore Repository를 생성한다.
     *
     * @param firestore Firestore Client
     * @param transactions Transaction 실행기
     * @param clock 저장 시각 기준 Clock
     */
    public FirestoreLineWebhookEventRepository(
            Firestore firestore,
            FirestoreTransactionRunner transactions,
            Clock clock
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Override
    public LineWebhookEventEntity save(LineWebhookEventEntity event) {
        if (transactions.isActive()) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            Instant createdAt = event.getCreatedAt() == null
                    ? now
                    : event.getCreatedAt();
            transactions.set(
                    document(event.getWebhookEventId()),
                    toDocument(event, createdAt, now)
            );
            event.markPersisted(now);
            return event;
        }
        Instant persistedAt = transactions.execute(() -> {
            DocumentSnapshot previous = transactions.get(
                    document(event.getWebhookEventId())
            );
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            Instant createdAt = previous.exists()
                    ? FirestoreDocumentValues.instant(previous, "createdAt")
                    : now;
            transactions.set(
                    document(event.getWebhookEventId()),
                    toDocument(event, createdAt, now)
            );
            return now;
        });
        event.markPersisted(persistedAt);
        return event;
    }

    @Override
    public List<LineWebhookEventEntity> saveAll(
            List<LineWebhookEventEntity> events
    ) {
        if (events.isEmpty()) {
            return List.of();
        }
        if (transactions.isActive()) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            for (LineWebhookEventEntity event : events) {
                Instant createdAt = event.getCreatedAt() == null
                        ? now
                        : event.getCreatedAt();
                transactions.set(
                        document(event.getWebhookEventId()),
                        toDocument(event, createdAt, now)
                );
                event.markPersisted(now);
            }
            return List.copyOf(events);
        }
        Instant persistedAt = transactions.execute(() -> {
            Map<String, DocumentSnapshot> previous = new HashMap<>();
            for (LineWebhookEventEntity event : events) {
                previous.put(
                        event.getWebhookEventId(),
                        transactions.get(document(event.getWebhookEventId()))
                );
            }
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            for (LineWebhookEventEntity event : events) {
                DocumentSnapshot snapshot = previous.get(event.getWebhookEventId());
                Instant createdAt = snapshot.exists()
                        ? FirestoreDocumentValues.instant(snapshot, "createdAt")
                        : now;
                transactions.set(
                        document(event.getWebhookEventId()),
                        toDocument(event, createdAt, now)
                );
            }
            return now;
        });
        events.forEach(event -> event.markPersisted(persistedAt));
        return List.copyOf(events);
    }

    @Override
    public Optional<LineWebhookEventEntity> findById(String webhookEventId) {
        if (webhookEventId == null || webhookEventId.isBlank()) {
            return Optional.empty();
        }
        DocumentSnapshot snapshot = transactions.get(document(webhookEventId));
        return snapshot.exists() ? Optional.of(fromDocument(snapshot)) : Optional.empty();
    }

    @Override
    public List<LineWebhookEventEntity> findAllById(Collection<String> ids) {
        List<LineWebhookEventEntity> result = new ArrayList<>();
        for (String id : ids) {
            findById(id).ifPresent(result::add);
        }
        return result;
    }

    @Override
    public int deleteTerminalCreatedBeforeWithoutDraft(Instant cutoff) {
        return transactions.execute(() -> {
            // Draft 수정 흐름은 원본 Event를 참조하므로 Draft보다 먼저 Event를 지우면 안 된다.
            Set<String> sourceEventIds = new HashSet<>();
            for (DocumentSnapshot draft : transactions.get(
                    firestore.collection(FirestoreCollections.LINE_VISIT_DRAFTS)
            ).getDocuments()) {
                String sourceEventId = draft.getString("sourceWebhookEventId");
                if (sourceEventId != null) {
                    sourceEventIds.add(sourceEventId);
                }
            }

            List<? extends DocumentSnapshot> expired = transactions.get(events())
                    .getDocuments()
                    .stream()
                    .filter(document -> {
                        String status = document.getString("status");
                        return LineWebhookEventStatus.COMPLETED.name().equals(status)
                                || LineWebhookEventStatus.FAILED.name().equals(status);
                    })
                    .filter(document -> {
                        Instant createdAt = FirestoreDocumentValues.instant(
                                document,
                                "createdAt"
                        );
                        return createdAt != null && createdAt.isBefore(cutoff);
                    })
                    .filter(document -> !sourceEventIds.contains(document.getId()))
                    .toList();
            expired.forEach(document -> transactions.delete(document.getReference()));
            return expired.size();
        });
    }

    private Map<String, Object> toDocument(
            LineWebhookEventEntity event,
            Instant createdAt,
            Instant updatedAt
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("webhookEventId", event.getWebhookEventId());
        document.put("lineUserId", event.getLineUserId());
        document.put("eventType", event.getEventType().name());
        document.put("messageText", event.getMessageText());
        document.put("postbackData", event.getPostbackData());
        document.put("status", event.getStatus().name());
        document.put("attemptCount", event.getAttemptCount());
        document.put("lastErrorCode", event.getLastErrorCode());
        document.put("revisionSourceDraftId", event.getRevisionSourceDraftId());
        document.put("occurredAt", FirestoreDocumentValues.timestamp(event.getOccurredAt()));
        document.put(
                "processingStartedAt",
                FirestoreDocumentValues.timestamp(event.getProcessingStartedAt())
        );
        document.put("processedAt", FirestoreDocumentValues.timestamp(event.getProcessedAt()));
        document.put("createdAt", FirestoreDocumentValues.timestamp(createdAt));
        document.put("updatedAt", FirestoreDocumentValues.timestamp(updatedAt));
        return document;
    }

    private LineWebhookEventEntity fromDocument(DocumentSnapshot document) {
        return LineWebhookEventEntity.restore(
                document.getId(),
                document.getString("lineUserId"),
                LineWebhookEventType.valueOf(document.getString("eventType")),
                document.getString("messageText"),
                document.getString("postbackData"),
                LineWebhookEventStatus.valueOf(document.getString("status")),
                FirestoreDocumentValues.integer(document, "attemptCount"),
                document.getString("lastErrorCode"),
                document.getLong("revisionSourceDraftId"),
                FirestoreDocumentValues.instant(document, "occurredAt"),
                FirestoreDocumentValues.instant(document, "processingStartedAt"),
                FirestoreDocumentValues.instant(document, "processedAt"),
                FirestoreDocumentValues.instant(document, "createdAt"),
                FirestoreDocumentValues.instant(document, "updatedAt")
        );
    }

    private CollectionReference events() {
        return firestore.collection(FirestoreCollections.LINE_WEBHOOK_EVENTS);
    }

    private DocumentReference document(String id) {
        return events().document(id);
    }
}
