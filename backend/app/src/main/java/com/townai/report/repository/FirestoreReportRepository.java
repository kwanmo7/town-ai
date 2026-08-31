package com.townai.report.repository;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.persistence.firestore.DuplicateDocumentException;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreIdGenerator;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import org.springframework.stereotype.Repository;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** GCS 객체 경로와 재사용 Fingerprint를 보존하는 Report Metadata의 Firestore 구현이다. */
@Repository
public class FirestoreReportRepository implements ReportRepository {

    private static final String COUNTER_NAMESPACE = "report";

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final FirestoreIdGenerator ids;
    private final Clock clock;

    /**
     * Report Metadata Firestore Repository를 생성한다.
     *
     * @param firestore Firestore Client
     * @param transactions Transaction 실행기
     * @param ids 숫자 ID 발급기
     * @param clock 저장 시각 기준 Clock
     */
    public FirestoreReportRepository(
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
    public ReportEntity save(ReportEntity report) {
        SaveResult result = transactions.execute(() -> {
            List<? extends DocumentSnapshot> existingReports = transactions
                    .get(reports())
                    .getDocuments();
            // 같은 LINE Event 재시도로 Report가 두 번 생성되는 것을 Transaction 안에서 차단한다.
            assertWebhookEventUnique(report, existingReports);

            DocumentSnapshot previous = report.getId() == null
                    ? null
                    : existingReports.stream()
                            .filter(document -> document.getId().equals(
                                    Long.toString(report.getId())
                            ))
                            .findFirst()
                            .orElse(null);
            long id = report.getId() == null
                    ? ids.next(COUNTER_NAMESPACE)
                    : report.getId();
            Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
            Instant createdAt = previous == null
                    ? now
                    : FirestoreDocumentValues.instant(previous, "createdAt");
            List<Long> targetAreaIds = previous == null
                    ? List.of()
                    : FirestoreDocumentValues.longs(previous, "targetAreaIds");
            // Metadata 수정 시 별도 Repository가 관리하는 분석 대상 순서를 덮어쓰지 않는다.
            transactions.set(
                    document(id),
                    toDocument(report, id, createdAt, now, targetAreaIds)
            );
            return new SaveResult(id, now);
        });
        report.markPersisted(result.id(), result.persistedAt());
        return report;
    }

    @Override
    public Optional<ReportEntity> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        DocumentSnapshot snapshot = transactions.get(document(id));
        return snapshot.exists() ? Optional.of(fromDocument(snapshot)) : Optional.empty();
    }

    @Override
    public List<ReportEntity> findAllByOrderByCreatedAtDescIdDesc() {
        return all().stream().sorted(recentFirst()).toList();
    }

    @Override
    public List<ReportEntity> findAllByReportTypeOrderByCreatedAtDescIdDesc(
            ReportType reportType
    ) {
        return all().stream()
                .filter(report -> report.getReportType() == reportType)
                .sorted(recentFirst())
                .toList();
    }

    @Override
    public Optional<ReportEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    ) {
        return all().stream()
                .filter(report -> sourceWebhookEventId.equals(
                        report.getSourceWebhookEventId()
                ))
                .findFirst();
    }

    @Override
    public Optional<ReportEntity>
            findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion,
                    String sourceFingerprint
            ) {
        return all().stream()
                .filter(report -> report.getReportType() == reportType)
                .filter(report -> report.getPromptVersion().equals(promptVersion))
                .filter(report -> sourceFingerprint.equals(
                        report.getSourceFingerprint()
                ))
                .sorted(recentFirst())
                .findFirst();
    }

    @Override
    public List<ReportEntity>
            findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion
            ) {
        return all().stream()
                .filter(report -> report.getReportType() == reportType)
                .filter(report -> report.getPromptVersion().equals(promptVersion))
                .filter(report -> report.getSourceFingerprint() == null)
                .sorted(recentFirst())
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        transactions.delete(document(id));
    }

    private void assertWebhookEventUnique(
            ReportEntity report,
            List<? extends DocumentSnapshot> existingReports
    ) {
        if (report.getSourceWebhookEventId() == null) {
            return;
        }
        boolean duplicate = existingReports.stream().anyMatch(document ->
                report.getSourceWebhookEventId().equals(
                        document.getString("sourceWebhookEventId")
                ) && !document.getId().equals(String.valueOf(report.getId()))
        );
        if (duplicate) {
            throw new DuplicateDocumentException(
                    "LINE Webhook Event already owns a Report."
            );
        }
    }

    private List<ReportEntity> all() {
        List<ReportEntity> result = new ArrayList<>();
        for (DocumentSnapshot snapshot : transactions.get(reports()).getDocuments()) {
            result.add(fromDocument(snapshot));
        }
        return result;
    }

    private Comparator<ReportEntity> recentFirst() {
        return Comparator.comparing(
                        ReportEntity::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(ReportEntity::getId)
                .reversed();
    }

    private ReportEntity fromDocument(DocumentSnapshot document) {
        return ReportEntity.restore(
                Long.parseLong(document.getId()),
                ReportType.valueOf(document.getString("reportType")),
                document.getString("model"),
                document.getString("promptVersion"),
                document.getString("sourceFingerprint"),
                document.getString("storagePath"),
                document.getString("sourceWebhookEventId"),
                FirestoreDocumentValues.instant(document, "createdAt"),
                FirestoreDocumentValues.instant(document, "updatedAt")
        );
    }

    private Map<String, Object> toDocument(
            ReportEntity report,
            long id,
            Instant createdAt,
            Instant updatedAt,
            List<Long> targetAreaIds
    ) {
        Map<String, Object> document = new HashMap<>();
        document.put("id", id);
        document.put("reportType", report.getReportType().name());
        document.put("model", report.getModel());
        document.put("promptVersion", report.getPromptVersion());
        document.put("sourceFingerprint", report.getSourceFingerprint());
        document.put("storagePath", report.getStoragePath());
        document.put("sourceWebhookEventId", report.getSourceWebhookEventId());
        document.put("targetAreaIds", targetAreaIds);
        document.put("createdAt", FirestoreDocumentValues.timestamp(createdAt));
        document.put("updatedAt", FirestoreDocumentValues.timestamp(updatedAt));
        return document;
    }

    private CollectionReference reports() {
        return firestore.collection(FirestoreCollections.REPORTS);
    }

    private DocumentReference document(Long id) {
        return reports().document(Long.toString(id));
    }

    private record SaveResult(long id, Instant persistedAt) {
    }
}
