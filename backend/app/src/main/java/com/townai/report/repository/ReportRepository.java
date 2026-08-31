package com.townai.report.repository;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;

import java.util.List;
import java.util.Optional;

/** GCS 본문과 연결되는 Report Metadata 문서의 영속성 경계이다. */
public interface ReportRepository {

    /**
     * Report Metadata를 저장하되 LINE 원본 Event의 중복 소유를 허용하지 않는다.
     *
     * @param report 저장할 Report
     * @return ID와 저장 시각이 반영된 Report
     */
    ReportEntity save(ReportEntity report);

    /**
     * Report Metadata를 ID로 조회한다.
     *
     * @param id Report ID
     * @return 존재하는 Report
     */
    Optional<ReportEntity> findById(Long id);

    /**
     * 전체 Report를 최근 생성 순으로 조회한다.
     *
     * @return Report 목록
     */
    List<ReportEntity> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 특정 유형의 Report를 최근 생성 순으로 조회한다.
     *
     * @param reportType Report 유형
     * @return 유형에 맞는 Report 목록
     */
    List<ReportEntity> findAllByReportTypeOrderByCreatedAtDescIdDesc(
            ReportType reportType
    );

    /**
     * LINE Event에서 이미 생성한 Report를 조회한다.
     *
     * @param sourceWebhookEventId 원본 LINE Event ID
     * @return 해당 Event가 소유한 Report
     */
    Optional<ReportEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    /**
     * 유형과 입력 Fingerprint가 같은 가장 최근 재사용 가능 Report를 찾는다.
     *
     * @param reportType Report 유형
     * @param promptVersion Prompt Version
     * @param sourceFingerprint 분석 입력 Fingerprint
     * @return 가장 최근의 재사용 가능 Report
     */
    Optional<ReportEntity>
            findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion,
                    String sourceFingerprint
            );

    /**
     * Fingerprint 도입 전에 생성한 같은 유형·Prompt Version의 Report를 조회한다.
     *
     * @param reportType Report 유형
     * @param promptVersion Prompt Version
     * @return Fingerprint가 없는 기존 Report 목록
     */
    List<ReportEntity>
            findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                    ReportType reportType,
                    String promptVersion
            );

    /**
     * Report Metadata를 삭제한다. GCS 본문 삭제는 Service 계층이 먼저 처리한다.
     *
     * @param id 삭제할 Report ID
     */
    void deleteById(Long id);
}
