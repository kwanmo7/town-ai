package com.townai.report.repository;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for Report metadata documents. */
public interface ReportRepository {

    ReportEntity save(ReportEntity report);

    Optional<ReportEntity> findById(Long id);

    List<ReportEntity> findAllByOrderByCreatedAtDescIdDesc();

    List<ReportEntity> findAllByReportTypeOrderByCreatedAtDescIdDesc(
            ReportType reportType
    );

    Optional<ReportEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    Optional<ReportEntity>
            findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion,
                    String sourceFingerprint
            );

    List<ReportEntity>
            findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion
            );

    void deleteById(Long id);
}
