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

/** Join Collection 대신 Report 문서에 정렬된 분석 대상 Area ID를 포함하는 구현이다. */
@Repository
public class FirestoreReportAreaRepository implements ReportAreaRepository {

    private final Firestore firestore;
    private final FirestoreTransactionRunner transactions;
    private final ReportRepository reportRepository;
    private final AreaRepository areaRepository;

    /**
     * Report 분석 대상 Area Repository를 생성한다.
     *
     * @param firestore Firestore Client
     * @param transactions Transaction 실행기
     * @param reportRepository Report Metadata 조회 경계
     * @param areaRepository Area 조회 경계
     */
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
        // Report와 Area의 관계 수가 작으므로 별도 Join 문서보다 배열 하나가 읽기 비용이 낮다.
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
            // 논리 삭제된 Area도 과거 Report 문맥에는 남아 있으므로 전체 ID 조회를 사용한다.
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
