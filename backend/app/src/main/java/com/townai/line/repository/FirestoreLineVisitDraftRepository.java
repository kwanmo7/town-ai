package com.townai.line.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import com.townai.persistence.firestore.DuplicateDocumentException;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreIdGenerator;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** LINE Visit Draft의 수정 Revision과 상태 전환을 보존하는 Firestore 구현이다. */
@Repository
public class FirestoreLineVisitDraftRepository
        implements LineVisitDraftRepository {

    private static final String COUNTER_NAMESPACE = "lineVisitDraft";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final FirestoreIdGenerator ids;
    private final AreaRepository areaRepository;
    private final VisitRepository visitRepository;
    private final Clock clock;

    /**
     * LINE Visit Draft Firestore Repository를 생성한다.
     *
     * @param firestore Firestore Client
     * @param transactions Transaction 실행기
     * @param ids 숫자 ID 발급기
     * @param areaRepository Area 참조 조회 경계
     * @param visitRepository 저장된 Visit 참조 조회 경계
     * @param clock 저장 시각 기준 Clock
     */
    public FirestoreLineVisitDraftRepository(
            Firestore firestore,
            FirestoreTransactionRunner transactions,
            FirestoreIdGenerator ids,
            AreaRepository areaRepository,
            VisitRepository visitRepository,
            Clock clock
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
        this.ids = ids;
        this.areaRepository = areaRepository;
        this.visitRepository = visitRepository;
        this.clock = clock;
    }

    @Override
    public LineVisitDraftEntity save(LineVisitDraftEntity draft) {
        return saveAll(List.of(draft)).getFirst();
    }

    @Override
    public List<LineVisitDraftEntity> saveAll(
            List<LineVisitDraftEntity> draftsToSave
    ) {
        if (draftsToSave.isEmpty()) {
            return List.of();
        }
        if (transactions.isActive()
                && draftsToSave.stream().allMatch(draft -> draft.getId() != null)) {
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            for (LineVisitDraftEntity draft : draftsToSave) {
                Instant createdAt = draft.getCreatedAt() == null
                        ? now
                        : draft.getCreatedAt();
                transactions.set(
                        document(draft.getId()),
                        toDocument(draft, draft.getId(), createdAt, now)
                );
                draft.markPersisted(draft.getId(), now);
            }
            return List.copyOf(draftsToSave);
        }
        List<SaveResult> results = transactions.execute(() -> {
            List<? extends DocumentSnapshot> existing = transactions.get(drafts())
                    .getDocuments();
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            int newDraftCount = Math.toIntExact(draftsToSave.stream()
                    .filter(draft -> draft.getId() == null)
                    .count());
            long[] allocatedIds = newDraftCount == 0
                    ? new long[0]
                    : ids.nextRange(COUNTER_NAMESPACE, newDraftCount).toArray();
            int nextAllocatedId = 0;
            List<SaveResult> saved = new ArrayList<>();
            for (LineVisitDraftEntity draft : draftsToSave) {
                assertUnique(draft, existing);
                DocumentSnapshot previous = draft.getId() == null
                        ? null
                        : existing.stream()
                                .filter(document -> document.getId().equals(
                                        Long.toString(draft.getId())
                                ))
                                .findFirst()
                                .orElse(null);
                long id = draft.getId() == null
                        ? allocatedIds[nextAllocatedId++]
                        : draft.getId();
                Instant createdAt = previous == null
                        ? now
                        : FirestoreDocumentValues.instant(previous, "createdAt");
                transactions.set(
                        document(id),
                        toDocument(draft, id, createdAt, now)
                );
                saved.add(new SaveResult(id, now));
            }
            return saved;
        });
        for (int index = 0; index < draftsToSave.size(); index++) {
            SaveResult result = results.get(index);
            draftsToSave.get(index).markPersisted(
                    result.id(),
                    result.persistedAt()
            );
        }
        return List.copyOf(draftsToSave);
    }

    @Override
    public Optional<LineVisitDraftEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    ) {
        return all().stream()
                .filter(draft -> sourceWebhookEventId.equals(
                        draft.getSourceWebhookEventId()
                ))
                .findFirst();
    }

    @Override
    public Optional<LineVisitDraftEntity>
            findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                    String lineUserId,
                    LineVisitDraftStatus status,
                    Instant currentTime
            ) {
        return all().stream()
                .filter(draft -> draft.getLineUserId().equals(lineUserId))
                .filter(draft -> draft.getStatus() == status)
                .filter(draft -> draft.getExpiresAt().isAfter(currentTime))
                .sorted(recentFirst())
                .findFirst();
    }

    @Override
    public List<LineVisitDraftEntity> findAllByLineUserIdAndStatus(
            String lineUserId,
            LineVisitDraftStatus status
    ) {
        return all().stream()
                .filter(draft -> draft.getLineUserId().equals(lineUserId))
                .filter(draft -> draft.getStatus() == status)
                .sorted(recentFirst())
                .toList();
    }

    @Override
    public List<LineVisitDraftEntity> findAllByLineUserIdAndStatusIn(
            String lineUserId,
            List<LineVisitDraftStatus> statuses
    ) {
        Set<LineVisitDraftStatus> selected = new HashSet<>(statuses);
        return all().stream()
                .filter(draft -> draft.getLineUserId().equals(lineUserId))
                .filter(draft -> selected.contains(draft.getStatus()))
                .sorted(recentFirst())
                .toList();
    }

    @Override
    public Optional<LineVisitDraftEntity> findByIdForUpdate(Long draftId) {
        if (draftId == null) {
            return Optional.empty();
        }
        DocumentSnapshot snapshot = transactions.get(document(draftId));
        return snapshot.exists() ? Optional.of(fromDocument(snapshot)) : Optional.empty();
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
        return transactions.execute(() -> {
            List<? extends DocumentSnapshot> expired = transactions.get(drafts())
                    .getDocuments()
                    .stream()
                    .filter(document -> {
                        Instant createdAt = FirestoreDocumentValues.instant(
                                document,
                                "createdAt"
                        );
                        return createdAt != null && createdAt.isBefore(cutoff);
                    })
                    .toList();
            expired.forEach(document -> transactions.delete(document.getReference()));
            return expired.size();
        });
    }

    private void assertUnique(
            LineVisitDraftEntity draft,
            List<? extends DocumentSnapshot> existing
    ) {
        boolean duplicateSource = existing.stream().anyMatch(document ->
                draft.getSourceWebhookEventId().equals(
                        document.getString("sourceWebhookEventId")
                ) && !document.getId().equals(String.valueOf(draft.getId()))
        );
        if (duplicateSource) {
            throw new DuplicateDocumentException(
                    "LINE Webhook Event already owns a Visit Draft."
            );
        }
        if (draft.getRevisionWebhookEventId() == null) {
            return;
        }
        boolean duplicateRevision = existing.stream().anyMatch(document ->
                draft.getRevisionWebhookEventId().equals(
                        document.getString("revisionWebhookEventId")
                ) && !document.getId().equals(String.valueOf(draft.getId()))
        );
        if (duplicateRevision) {
            throw new DuplicateDocumentException(
                    "LINE revision event is already claimed."
            );
        }
    }

    private List<LineVisitDraftEntity> all() {
        List<LineVisitDraftEntity> result = new ArrayList<>();
        for (DocumentSnapshot snapshot : transactions.get(drafts()).getDocuments()) {
            result.add(fromDocument(snapshot));
        }
        return result;
    }

    private Comparator<LineVisitDraftEntity> recentFirst() {
        return Comparator.comparing(
                        LineVisitDraftEntity::getUpdatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(LineVisitDraftEntity::getId)
                .reversed();
    }

    private LineVisitDraftEntity fromDocument(DocumentSnapshot document) {
        Long areaId = document.getLong("areaId");
        AreaEntity area = areaId == null
                ? null
                : areaRepository.findById(areaId).orElse(null);
        Long confirmedVisitId = document.getLong("confirmedVisitId");
        VisitEntity confirmedVisit = confirmedVisitId == null
                ? null
                : visitRepository.findById(confirmedVisitId).orElse(null);
        return LineVisitDraftEntity.restore(
                Long.parseLong(document.getId()),
                document.getString("sourceWebhookEventId"),
                document.getString("lineUserId"),
                area,
                Boolean.TRUE.equals(document.getBoolean("areaRegistrationRequired")),
                document.getString("areaName"),
                document.getString("areaPrefecture"),
                document.getString("areaCity"),
                document.getString("areaStation"),
                FirestoreDocumentValues.localDate(document, "visitDate"),
                FirestoreDocumentValues.nullableInteger(document, "atmosphereScore"),
                FirestoreDocumentValues.nullableInteger(document, "infraScore"),
                FirestoreDocumentValues.nullableInteger(document, "cleanScore"),
                FirestoreDocumentValues.nullableInteger(document, "sizeScore"),
                FirestoreDocumentValues.nullableInteger(document, "accessScore"),
                document.getString("memo"),
                FirestoreDocumentValues.strings(document, "warnings"),
                LineVisitDraftStatus.valueOf(document.getString("status")),
                FirestoreDocumentValues.instant(document, "expiresAt"),
                document.getString("revisionWebhookEventId"),
                confirmedVisit,
                FirestoreDocumentValues.instant(document, "createdAt"),
                FirestoreDocumentValues.instant(document, "updatedAt")
        );
    }

    private Map<String, Object> toDocument(
            LineVisitDraftEntity draft,
            long id,
            Instant createdAt,
            Instant updatedAt
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("sourceWebhookEventId", draft.getSourceWebhookEventId());
        document.put("lineUserId", draft.getLineUserId());
        document.put("areaId", draft.getArea() == null ? null : draft.getArea().getId());
        document.put("areaRegistrationRequired", draft.isAreaRegistrationRequired());
        document.put("areaName", draft.getAreaName());
        document.put("areaPrefecture", draft.getAreaPrefecture());
        document.put("areaCity", draft.getAreaCity());
        document.put("areaStation", draft.getAreaStation());
        document.put("visitDate", FirestoreDocumentValues.localDate(draft.getVisitDate()));
        document.put("atmosphereScore", draft.getAtmosphereScore());
        document.put("infraScore", draft.getInfraScore());
        document.put("cleanScore", draft.getCleanScore());
        document.put("sizeScore", draft.getSizeScore());
        document.put("accessScore", draft.getAccessScore());
        document.put("memo", draft.getMemo());
        document.put("warnings", draft.getWarnings());
        document.put("status", draft.getStatus().name());
        document.put("expiresAt", FirestoreDocumentValues.timestamp(draft.getExpiresAt()));
        document.put("revisionWebhookEventId", draft.getRevisionWebhookEventId());
        document.put(
                "confirmedVisitId",
                draft.getConfirmedVisit() == null
                        ? null
                        : draft.getConfirmedVisit().getId()
        );
        document.put("createdAt", FirestoreDocumentValues.timestamp(createdAt));
        document.put("updatedAt", FirestoreDocumentValues.timestamp(updatedAt));
        return document;
    }

    private CollectionReference drafts() {
        return firestore.collection(FirestoreCollections.LINE_VISIT_DRAFTS);
    }

    private DocumentReference document(Long id) {
        return drafts().document(Long.toString(id));
    }

    private record SaveResult(long id, Instant persistedAt) {
    }
}
