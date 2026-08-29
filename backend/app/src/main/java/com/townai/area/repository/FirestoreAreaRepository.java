package com.townai.area.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.area.entity.AreaEntity;
import com.townai.persistence.firestore.DuplicateDocumentException;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreIdGenerator;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Firestore implementation of the Area persistence boundary. */
@Repository
public class FirestoreAreaRepository implements AreaRepository {

    private static final String COUNTER_NAMESPACE = "area";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final FirestoreIdGenerator ids;
    private final Clock clock;

    public FirestoreAreaRepository(
            Firestore firestore,
            FirestoreTransactionRunner transactions,
            FirestoreIdGenerator ids,
            Clock clock
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    public AreaEntity save(AreaEntity area) {
        SaveResult result = transactions.execute(() -> saveInTransaction(area));
        area.markPersisted(result.id(), result.persistedAt());
        return area;
    }

    private SaveResult saveInTransaction(AreaEntity area) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        String newKey = areaKey(area.getPrefecture(), area.getCity(), area.getName());
        DocumentReference newKeyReference = keyDocument(newKey);
        DocumentSnapshot reservedKey = transactions.get(newKeyReference);

        Long id = area.getId();
        DocumentSnapshot previous = null;
        String oldKey = null;
        if (id != null) {
            previous = transactions.get(areaDocument(id));
            if (!previous.exists()) {
                throw new IllegalStateException("Area does not exist: " + id);
            }
            oldKey = areaKey(
                    previous.getString("prefecture"),
                    previous.getString("city"),
                    previous.getString("name")
            );
        }

        Long reservedAreaId = reservedKey.exists()
                ? reservedKey.getLong("areaId")
                : null;
        if (reservedAreaId != null && !reservedAreaId.equals(id)) {
            throw new DuplicateDocumentException("Area location is already reserved.");
        }

        long persistedId = id == null ? ids.next(COUNTER_NAMESPACE) : id;
        Instant createdAt = previous == null
                ? now
                : FirestoreDocumentValues.instant(previous, "createdAt");
        transactions.set(
                areaDocument(persistedId),
                toDocument(area, persistedId, createdAt, now)
        );
        transactions.set(newKeyReference, Map.of("areaId", persistedId));
        if (oldKey != null && !oldKey.equals(newKey)) {
            transactions.delete(keyDocument(oldKey));
        }
        return new SaveResult(persistedId, now);
    }

    @Override
    public List<AreaEntity> findAllByDeletedAtIsNullOrderByIdAsc() {
        return all().stream()
                .filter(area -> area.getDeletedAt() == null)
                .sorted(Comparator.comparing(AreaEntity::getId))
                .toList();
    }

    @Override
    public long countByDeletedAtIsNull() {
        return findAllByDeletedAtIsNullOrderByIdAsc().size();
    }

    @Override
    public Optional<AreaEntity> findByIdAndDeletedAtIsNull(Long id) {
        return findById(id).filter(area -> area.getDeletedAt() == null);
    }

    @Override
    public Optional<AreaEntity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DocumentSnapshot snapshot = transactions.get(areaDocument(id));
        return snapshot.exists() ? Optional.of(fromDocument(snapshot)) : Optional.empty();
    }

    @Override
    public Optional<AreaEntity> findByPrefectureAndCityAndNameAndDeletedAtIsNull(
            String prefecture,
            String city,
            String name
    ) {
        return all().stream()
                .filter(area -> area.getDeletedAt() == null)
                .filter(area -> area.getPrefecture().equals(prefecture))
                .filter(area -> area.getCity().equals(city))
                .filter(area -> area.getName().equals(name))
                .findFirst();
    }

    @Override
    public boolean existsByPrefectureAndCityAndName(
            String prefecture,
            String city,
            String name
    ) {
        return transactions.get(keyDocument(areaKey(prefecture, city, name))).exists();
    }

    @Override
    public boolean existsByPrefectureAndCityAndNameAndIdNot(
            String prefecture,
            String city,
            String name,
            Long id
    ) {
        DocumentSnapshot key = transactions.get(
                keyDocument(areaKey(prefecture, city, name))
        );
        Long areaId = key.exists() ? key.getLong("areaId") : null;
        return areaId != null && !areaId.equals(id);
    }

    @Override
    public boolean existsChangedAfter(Instant since) {
        return all().stream().anyMatch(area -> changedAfter(area, since));
    }

    @Override
    public boolean existsChangedAfterForAreaIds(
            List<Long> areaIds,
            Instant since
    ) {
        return all().stream()
                .filter(area -> areaIds.contains(area.getId()))
                .anyMatch(area -> changedAfter(area, since));
    }

    private boolean changedAfter(AreaEntity area, Instant since) {
        return (area.getUpdatedAt() != null && area.getUpdatedAt().isAfter(since))
                || (area.getDeletedAt() != null && area.getDeletedAt().isAfter(since));
    }

    private List<AreaEntity> all() {
        List<AreaEntity> result = new ArrayList<>();
        for (DocumentSnapshot document : transactions.get(areas()).getDocuments()) {
            result.add(fromDocument(document));
        }
        return result;
    }

    private CollectionReference areas() {
        return firestore.collection(FirestoreCollections.AREAS);
    }

    private DocumentReference areaDocument(Long id) {
        return areas().document(Long.toString(id));
    }

    private DocumentReference keyDocument(String key) {
        return firestore.collection(FirestoreCollections.AREA_KEYS).document(key);
    }

    private Map<String, Object> toDocument(
            AreaEntity area,
            long id,
            Instant createdAt,
            Instant updatedAt
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("name", area.getName());
        document.put("prefecture", area.getPrefecture());
        document.put("city", area.getCity());
        document.put("station", area.getStation());
        document.put("createdAt", FirestoreDocumentValues.timestamp(createdAt));
        document.put("updatedAt", FirestoreDocumentValues.timestamp(updatedAt));
        document.put("deletedAt", FirestoreDocumentValues.timestamp(area.getDeletedAt()));
        return document;
    }

    private AreaEntity fromDocument(DocumentSnapshot document) {
        return AreaEntity.restore(
                Long.parseLong(document.getId()),
                document.getString("name"),
                document.getString("prefecture"),
                document.getString("city"),
                document.getString("station"),
                FirestoreDocumentValues.instant(document, "createdAt"),
                FirestoreDocumentValues.instant(document, "updatedAt"),
                FirestoreDocumentValues.instant(document, "deletedAt")
        );
    }

    private String areaKey(String prefecture, String city, String name) {
        String value = prefecture + '\u0000' + city + '\u0000' + name;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private record SaveResult(long id, Instant persistedAt) {
    }
}
