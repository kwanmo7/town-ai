package com.townai.report.repository;

import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.persistence.firestore.FirestoreCollections;
import com.townai.persistence.firestore.FirestoreDocumentValues;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.report.entity.ReportAreaEntity;
import com.townai.report.entity.ReportEntity;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Embeds ordered Report target IDs instead of maintaining a join collection. */
@Repository
public class FirestoreReportAreaRepository implements ReportAreaRepository {

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final ReportRepository reportRepository;
    private final AreaRepository areaRepository;

    public FirestoreReportAreaRepository(
            Firestore firestore,
            FirestoreTransactionRunner transactions,
            ReportRepository reportRepository,
            AreaRepository areaRepository
    ) {
        this.firestore = firestore;
        this.transactions = transactions;
        this.reportRepository = reportRepository;
        this.areaRepository = areaRepository;
    }

    @Override
    public List<ReportAreaEntity> saveAll(List<ReportAreaEntity> reportAreas) {
        if (reportAreas.isEmpty()) {
            return List.of();
        }
        Long reportId = reportAreas.getFirst().getReport().getId();
        List<Long> areaIds = reportAreas.stream()
                .sorted(java.util.Comparator.comparing(
                        ReportAreaEntity::getDisplayOrder
                ))
                .map(reportArea -> reportArea.getArea().getId())
                .toList();
        transactions.merge(
                document(reportId),
                Map.of("targetAreaIds", areaIds)
        );
        return List.copyOf(reportAreas);
    }

    @Override
    public List<ReportAreaEntity> findAllWithAreaByReportId(Long reportId) {
        DocumentSnapshot snapshot = transactions.get(document(reportId));
        if (!snapshot.exists()) {
            return List.of();
        }
        ReportEntity report = reportRepository.findById(reportId).orElseThrow();
        List<ReportAreaEntity> result = new ArrayList<>();
        int displayOrder = 1;
        for (Long areaId : FirestoreDocumentValues.longs(
                snapshot,
                "targetAreaIds"
        )) {
            AreaEntity area = areaRepository.findById(areaId).orElse(null);
            if (area != null) {
                result.add(new ReportAreaEntity(report, area, displayOrder));
            }
            displayOrder++;
        }
        return result;
    }

    @Override
    public void deleteAllByReportId(Long reportId) {
        transactions.merge(
                document(reportId),
                Map.of("targetAreaIds", List.of())
        );
    }

    private DocumentReference document(Long reportId) {
        return firestore.collection(FirestoreCollections.REPORTS)
                .document(Long.toString(reportId));
    }
}
