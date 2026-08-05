package com.townai.report.reuse;

import com.townai.area.repository.AreaRepository;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportGenerationData;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.visit.repository.VisitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 현재 Prompt 입력과 같은 Report를 찾고 Migration 이전 Report를 안전하게 승격한다.
 */
@Service
public class ReportReuseService {

    private final ReportRepository reportRepository;
    private final ReportAreaRepository reportAreaRepository;
    private final AreaRepository areaRepository;
    private final VisitRepository visitRepository;

    /**
     * Report 재사용 판단에 필요한 메타데이터와 원본 변경 조회기를 구성한다.
     *
     * @param reportRepository Report 메타데이터 Repository
     * @param reportAreaRepository Report 대상 Area Repository
     * @param areaRepository Area 변경 확인 Repository
     * @param visitRepository Visit 변경 확인 Repository
     */
    public ReportReuseService(
            ReportRepository reportRepository,
            ReportAreaRepository reportAreaRepository,
            AreaRepository areaRepository,
            VisitRepository visitRepository
    ) {
        this.reportRepository = reportRepository;
        this.reportAreaRepository = reportAreaRepository;
        this.areaRepository = areaRepository;
        this.visitRepository = visitRepository;
    }

    /**
     * 동일 지문 Report를 우선 조회하고, 지문 도입 전 최신 Report도 조건부 재사용한다.
     *
     * <p>Legacy Report는 대상 Area가 현재 입력과 같고 생성 이후 관련 Area·Visit이
     * 변경되지 않은 경우에만 현재 지문을 연결한다.</p>
     *
     * @param data 현재 DB로 조립한 Report 입력
     * @param sourceFingerprint 현재 입력의 SHA-256 지문
     * @return 그대로 조회할 수 있는 기존 Report
     */
    @Transactional
    public Optional<ReportEntity> findReusable(
            ReportGenerationData data,
            String sourceFingerprint
    ) {
        Optional<ReportEntity> exact = reportRepository
                .findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                        data.reportType(),
                        data.reportType().promptVersion(),
                        sourceFingerprint
                );
        if (exact.isPresent()) {
            return exact;
        }

        List<Long> currentAreaIds = data.targetAreas().stream()
                .map(area -> area.getId())
                .toList();
        return reportRepository
                .findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                        data.reportType(),
                        data.reportType().promptVersion()
                )
                .stream()
                .filter(report -> hasSameTargets(report, currentAreaIds))
                .filter(report -> !hasSourceChanged(data.reportType(), currentAreaIds, report.getCreatedAt()))
                .findFirst()
                .map(report -> {
                    report.assignSourceFingerprint(sourceFingerprint);
                    return reportRepository.save(report);
                });
    }

    private boolean hasSameTargets(
            ReportEntity report,
            List<Long> currentAreaIds
    ) {
        List<Long> reportAreaIds = reportAreaRepository
                .findAllWithAreaByReportId(report.getId())
                .stream()
                .map(reportArea -> reportArea.getArea().getId())
                .toList();
        return reportAreaIds.equals(currentAreaIds);
    }

    private boolean hasSourceChanged(
            ReportType reportType,
            List<Long> areaIds,
            Instant reportCreatedAt
    ) {
        if (reportCreatedAt == null) {
            return true;
        }
        if (reportType == ReportType.SUMMARY || reportType == ReportType.ALL) {
            return areaRepository.existsChangedAfter(reportCreatedAt)
                    || visitRepository.existsByUpdatedAtAfter(reportCreatedAt);
        }
        return areaRepository.existsChangedAfterForAreaIds(
                areaIds,
                reportCreatedAt
        ) || visitRepository.existsByArea_IdInAndUpdatedAtAfter(
                areaIds,
                reportCreatedAt
        );
    }
}
