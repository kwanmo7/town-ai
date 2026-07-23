package com.townai.report.persistence;

import com.townai.area.entity.AreaEntity;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.entity.ReportAreaEntity;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.report.storage.ReportStorage;
import com.townai.report.storage.ReportStorageException;
import com.townai.report.storage.ReportStoragePathFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Report DB 메타데이터와 외부 Storage 사이의 저장 순서 및 실패 보상을 담당한다.
 *
 * <p>Report ID가 Storage 파일명에 필요하므로 Transaction 안에서 Row를 먼저 저장해
 * ID를 얻고, 본문을 Storage에 쓴 뒤 경로와 대상 Area 연결을 확정한다. 외부 Storage는
 * DB Transaction에 참여하지 못하므로 이후 단계가 실패하면 작성한 객체를 최선
 * 노력(best effort)으로 삭제해 고아 파일을 보상한다.</p>
 */
@Component
public class ReportPersistenceService {

    private static final Logger log = LoggerFactory.getLogger(ReportPersistenceService.class);

    private final ReportRepository reportRepository;
    private final ReportAreaRepository reportAreaRepository;
    private final ReportStorage reportStorage;
    private final ReportStoragePathFactory pathFactory;
    private final TransactionTemplate transactionTemplate;

    /**
     * DB Transaction과 외부 Storage 저장을 조정하는 Component를 만든다.
     *
     * @param reportRepository Report 메타데이터 Repository
     * @param reportAreaRepository Report 대상 Area 연결 Repository
     * @param reportStorage Markdown 본문 저장소
     * @param pathFactory 논리 Storage 경로 생성기
     * @param transactionManager DB Transaction 관리자
     */
    public ReportPersistenceService(
            ReportRepository reportRepository,
            ReportAreaRepository reportAreaRepository,
            ReportStorage reportStorage,
            ReportStoragePathFactory pathFactory,
            PlatformTransactionManager transactionManager
    ) {
        this.reportRepository = reportRepository;
        this.reportAreaRepository = reportAreaRepository;
        this.reportStorage = reportStorage;
        this.pathFactory = pathFactory;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Report ID 선점, 본문 저장, 대상 Area 연결과 경로 확정을 수행한다.
     *
     * @param reportType 생성한 Report 유형
     * @param model 실제 사용된 OpenAI 모델
     * @param promptVersion 적용한 Prompt 버전
     * @param markdown 검증과 조립을 마친 최종 Markdown
     * @param targetAreas 생성 당시 분석 대상 Area. 요청 표시 순서
     * @return Storage 경로가 확정된 Report Entity
     * @throws ApiException Storage 접근에 실패한 경우
     */
    public ReportEntity persist(
            ReportType reportType,
            String model,
            String promptVersion,
            String markdown,
            List<AreaEntity> targetAreas
    ) {
        AtomicReference<String> attemptedStoragePath = new AtomicReference<>();
        try {
            ReportEntity result = transactionTemplate.execute(status -> {
                ReportEntity report = ReportEntity.builder()
                        .reportType(reportType)
                        .model(model)
                        .promptVersion(promptVersion)
                        .build();
                reportRepository.saveAndFlush(report);

                String storagePath = pathFactory.create(
                        reportType,
                        report.getId(),
                        targetAreas
                );
                attemptedStoragePath.set(storagePath);
                reportStorage.write(storagePath, markdown);
                report.assignStoragePath(storagePath);

                List<ReportAreaEntity> reportAreas = new ArrayList<>();
                for (int index = 0; index < targetAreas.size(); index++) {
                    reportAreas.add(new ReportAreaEntity(
                            report,
                            targetAreas.get(index),
                            index + 1
                    ));
                }
                reportAreaRepository.saveAll(reportAreas);
                return reportRepository.saveAndFlush(report);
            });
            if (result == null) {
                throw new IllegalStateException("Report transaction returned no result.");
            }
            return result;
        } catch (RuntimeException exception) {
            compensateStorage(attemptedStoragePath.get());
            if (exception instanceof ReportStorageException) {
                throw new ApiException(ErrorCode.STORAGE_ERROR);
            }
            throw exception;
        }
    }

    /**
     * Storage 객체가 먼저 삭제된 후 호출되며 관계 Row와 Report Row를 한 Transaction에서 삭제한다.
     *
     * @param reportId 메타데이터를 제거할 Report ID
     */
    public void deleteMetadata(Long reportId) {
        transactionTemplate.executeWithoutResult(status -> {
            reportAreaRepository.deleteAllByReportId(reportId);
            reportRepository.deleteById(reportId);
            reportRepository.flush();
        });
    }

    private void compensateStorage(String storagePath) {
        if (storagePath == null) {
            return;
        }
        try {
            reportStorage.delete(storagePath);
        } catch (RuntimeException compensationFailure) {
            log.error(
                    "Failed to compensate orphan Report storage object. storagePath={}",
                    storagePath,
                    compensationFailure
            );
        }
    }
}
