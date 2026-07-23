package com.townai.report.service.impl;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportDetailResponse;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.GeneratedReportContent;
import com.townai.report.generation.ReportContentGenerator;
import com.townai.report.generation.ReportDataAssembler;
import com.townai.report.generation.ReportGenerationData;
import com.townai.report.persistence.ReportPersistenceService;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.report.service.ReportService;
import com.townai.report.storage.ReportStorage;
import com.townai.report.storage.ReportStorageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Report 생성과 조회·삭제 Use Case의 최상위 흐름을 조정한다.
 *
 * <p>생성은 요청·DB 입력 준비 → AI 생성 및 출력 검증 → Storage와 메타데이터 저장
 * 순서로 동기 실행한다. 삭제는 설계 정책에 따라 Storage를 먼저 지우고 DB를 삭제한다.
 * 따라서 Storage 삭제 실패 시 DB를 보존하며, Storage 성공 후 DB 실패는 운영 복구가
 * 필요한 상태로 Error 로그에 남긴다.</p>
 */
@Service
public class ReportServiceImpl implements ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportServiceImpl.class);

    private final ReportDataAssembler dataAssembler;
    private final ReportContentGenerator contentGenerator;
    private final ReportPersistenceService persistenceService;
    private final ReportRepository reportRepository;
    private final ReportAreaRepository reportAreaRepository;
    private final ReportStorage reportStorage;

    /**
     * Report Use Case 조정 Service를 생성한다.
     *
     * @param dataAssembler 요청 검증과 Prompt 입력 조립 Component
     * @param contentGenerator AI 호출과 출력 검증 Component
     * @param persistenceService DB·Storage 저장 순서 조정 Component
     * @param reportRepository Report 메타데이터 Repository
     * @param reportAreaRepository 생성 대상 Area 연결 Repository
     * @param reportStorage Markdown 본문 저장소
     */
    public ReportServiceImpl(
            ReportDataAssembler dataAssembler,
            ReportContentGenerator contentGenerator,
            ReportPersistenceService persistenceService,
            ReportRepository reportRepository,
            ReportAreaRepository reportAreaRepository,
            ReportStorage reportStorage
    ) {
        this.dataAssembler = dataAssembler;
        this.contentGenerator = contentGenerator;
        this.persistenceService = persistenceService;
        this.reportRepository = reportRepository;
        this.reportAreaRepository = reportAreaRepository;
        this.reportStorage = reportStorage;
    }

    @Override
    public ReportResponse create(ReportCreateRequest request) {
        Instant startedAt = Instant.now();
        ReportGenerationData data = dataAssembler.prepare(request);
        try {
            GeneratedReportContent content = contentGenerator.generate(data);
            ReportEntity report = persistenceService.persist(
                    data.reportType(),
                    content.model(),
                    content.promptVersion(),
                    content.markdown(),
                    data.targetAreas()
            );
            log.info(
                    "Report generation succeeded. reportId={}, type={}, durationMs={}",
                    report.getId(),
                    report.getReportType(),
                    Duration.between(startedAt, Instant.now()).toMillis()
            );
            return ReportResponse.from(report);
        } catch (RuntimeException exception) {
            log.error(
                    "Report generation failed. type={}, durationMs={}",
                    data.reportType(),
                    Duration.between(startedAt, Instant.now()).toMillis(),
                    exception
            );
            throw exception;
        }
    }

    @Override
    public List<ReportResponse> findAll(String reportType) {
        List<ReportEntity> reports;
        if (reportType == null || reportType.isBlank()) {
            reports = reportRepository.findAllByOrderByCreatedAtDescIdDesc();
        } else {
            reports = reportRepository.findAllByReportTypeOrderByCreatedAtDescIdDesc(
                    ReportType.from(reportType)
            );
        }
        return reports.stream().map(ReportResponse::from).toList();
    }

    @Override
    public ReportDetailResponse findById(Long reportId) {
        ReportEntity report = findReport(reportId);
        List<Long> areaIds = reportAreaRepository.findAllWithAreaByReportId(reportId)
                .stream()
                .map(reportArea -> reportArea.getArea().getId())
                .toList();
        return ReportDetailResponse.from(report, areaIds);
    }

    @Override
    public String getContent(Long reportId) {
        ReportEntity report = findReport(reportId);
        try {
            return reportStorage.read(report.getStoragePath());
        } catch (ReportStorageException exception) {
            log.error(
                    "Failed to read Report content. reportId={}, storagePath={}",
                    reportId,
                    report.getStoragePath(),
                    exception
            );
            throw new ApiException(ErrorCode.STORAGE_ERROR);
        }
    }

    @Override
    public void delete(Long reportId) {
        ReportEntity report = findReport(reportId);
        try {
            reportStorage.delete(report.getStoragePath());
        } catch (ReportStorageException exception) {
            log.error(
                    "Failed to delete Report storage object. reportId={}, storagePath={}",
                    reportId,
                    report.getStoragePath(),
                    exception
            );
            throw new ApiException(ErrorCode.STORAGE_ERROR);
        }

        try {
            persistenceService.deleteMetadata(reportId);
        } catch (RuntimeException exception) {
            log.error(
                    "Report storage was deleted but DB metadata deletion failed. "
                            + "reportId={}, storagePath={}",
                    reportId,
                    report.getStoragePath(),
                    exception
            );
            throw exception;
        }
    }

    private ReportEntity findReport(Long reportId) {
        return reportRepository.findById(reportId)
                .orElseThrow(() -> new ApiException(ErrorCode.REPORT_NOT_FOUND));
    }
}
