package com.townai.visit.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreIdGenerator;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.visit.entity.VisitEntity;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Firestore implementation of Visit persistence and low-volume report queries. */
@Repository
public class FirestoreVisitRepository implements VisitRepository {

    private static final String COUNTER_NAMESPACE = "visit";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final FirestoreIdGenerator ids;
    private final AreaRepository areaRepository;
    private final Clock clock;

    public FirestoreVisitRepository(
            Firestore firestore,
            FirestoreTransactionRunner transactions,
            FirestoreIdGenerator ids,
            AreaRepository areaRepository,
            Clock clock
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
        this.ids = ids;
        this.areaRepository = areaRepository;
        this.clock = clock;
    }

    @Override
    public VisitEntity save(VisitEntity visit) {
        SaveResult result = transactions.execute(() -> {
            long id = visit.getId() == null
                    ? ids.next(COUNTER_NAMESPACE)
                    : visit.getId();
            DocumentSnapshot previous = visit.getId() == null
                    ? null
                    : transactions.get(document(id));
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            Instant createdAt = previous == null || !previous.exists()
                    ? now
                    : FirestoreDocumentValues.instant(previous, "createdAt");
            transactions.set(document(id), toDocument(visit, id, createdAt, now));
            return new SaveResult(id, now);
        });
        visit.markPersisted(result.id(), result.persistedAt());
        return visit;
    }

    @Override
    public Optional<VisitEntity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DocumentSnapshot snapshot = transactions.get(document(id));
        return snapshot.exists() ? fromDocument(snapshot) : Optional.empty();
    }

    @Override
    public List<VisitEntity> findAllByFilters(
            Long areaId,
            LocalDate fromDate,
            LocalDate toDate
    ) {
        return all().stream()
                .filter(visit -> visit.getArea().getDeletedAt() == null)
                .filter(visit -> areaId == null
                        || visit.getArea().getId().equals(areaId))
                .filter(visit -> fromDate == null
                        || !visit.getVisitDate().isBefore(fromDate))
                .filter(visit -> toDate == null
                        || !visit.getVisitDate().isAfter(toDate))
                .sorted(Comparator.comparing(VisitEntity::getVisitDate)
                        .thenComparing(VisitEntity::getId)
                        .reversed())
                .toList();
    }

    @Override
    public List<VisitEntity> findAllForActiveAreas() {
        return all().stream()
                .filter(visit -> visit.getArea().getDeletedAt() == null)
                .sorted(reportOrder())
                .toList();
    }

    @Override
    public List<VisitEntity> findAllByAreaIdsForReport(List<Long> areaIds) {
        Set<Long> selected = new HashSet<>(areaIds);
        return all().stream()
                .filter(visit -> selected.contains(visit.getArea().getId()))
                .sorted(reportOrder())
                .toList();
    }

    @Override
    public boolean existsByUpdatedAtAfter(Instant since) {
        return all().stream().anyMatch(visit -> visit.getUpdatedAt() != null
                && visit.getUpdatedAt().isAfter(since));
    }

    @Override
    public boolean existsByArea_IdInAndUpdatedAtAfter(
            List<Long> areaIds,
            Instant since
    ) {
        Set<Long> selected = new HashSet<>(areaIds);
        return all().stream()
                .filter(visit -> selected.contains(visit.getArea().getId()))
                .anyMatch(visit -> visit.getUpdatedAt() != null
                        && visit.getUpdatedAt().isAfter(since));
    }

    @Override
    public void delete(VisitEntity visit) {
        if (visit.getId() != null) {
            transactions.delete(document(visit.getId()));
        }
    }

    private Comparator<VisitEntity> reportOrder() {
        return Comparator.comparing((VisitEntity visit) -> visit.getArea().getId())
                .thenComparing(VisitEntity::getVisitDate)
                .thenComparing(VisitEntity::getId);
    }

    private List<VisitEntity> all() {
        List<VisitEntity> result = new ArrayList<>();
        for (DocumentSnapshot snapshot : transactions.get(visits()).getDocuments()) {
            fromDocument(snapshot).ifPresent(result::add);
        }
        return result;
    }

    private Optional<VisitEntity> fromDocument(DocumentSnapshot document) {
        Long areaId = document.getLong("areaId");
        if (areaId == null) {
            return Optional.empty();
        }
        Optional<AreaEntity> area = areaRepository.findById(areaId);
        if (area.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(VisitEntity.restore(
                Long.parseLong(document.getId()),
                area.get(),
                FirestoreDocumentValues.localDate(document, "visitDate"),
                FirestoreDocumentValues.integer(document, "atmosphereScore"),
                FirestoreDocumentValues.integer(document, "infraScore"),
                FirestoreDocumentValues.integer(document, "cleanScore"),
                FirestoreDocumentValues.integer(document, "sizeScore"),
                FirestoreDocumentValues.integer(document, "accessScore"),
                document.getString("memo"),
                FirestoreDocumentValues.instant(document, "createdAt"),
                FirestoreDocumentValues.instant(document, "updatedAt")
        ));
    }

    private Map<String, Object> toDocument(
            VisitEntity visit,
            long id,
            Instant createdAt,
            Instant updatedAt
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("areaId", visit.getArea().getId());
        document.put("visitDate", FirestoreDocumentValues.localDate(visit.getVisitDate()));
        document.put("atmosphereScore", visit.getAtmosphereScore());
        document.put("infraScore", visit.getInfraScore());
        document.put("cleanScore", visit.getCleanScore());
        document.put("sizeScore", visit.getSizeScore());
        document.put("accessScore", visit.getAccessScore());
        document.put("memo", visit.getMemo());
        document.put("createdAt", FirestoreDocumentValues.timestamp(createdAt));
        document.put("updatedAt", FirestoreDocumentValues.timestamp(updatedAt));
        return document;
    }

    private CollectionReference visits() {
        return firestore.collection(FirestoreCollections.VISITS);
    }

    private DocumentReference document(Long id) {
        return visits().document(Long.toString(id));
    }

    private record SaveResult(long id, Instant persistedAt) {
    }
}
