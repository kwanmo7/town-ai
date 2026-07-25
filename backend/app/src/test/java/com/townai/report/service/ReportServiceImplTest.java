package com.townai.report.service;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportContentGenerator;
import com.townai.report.generation.ReportDataAssembler;
import com.townai.report.persistence.ReportPersistenceService;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.report.service.impl.ReportServiceImpl;
import com.townai.report.storage.ReportStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceImplTest {

    @Mock
    private ReportDataAssembler dataAssembler;

    @Mock
    private ReportContentGenerator contentGenerator;

    @Mock
    private ReportPersistenceService persistenceService;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAreaRepository reportAreaRepository;

    @Mock
    private ReportStorage reportStorage;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                dataAssembler,
                contentGenerator,
                persistenceService,
                reportRepository,
                reportAreaRepository,
                reportStorage
        );
    }

    @Test
    void deletesStorageBeforeDatabaseMetadata() {
        ReportEntity report = ReportEntity.builder()
                .reportType(ReportType.AREA)
                .model("test-model")
                .promptVersion("area-v1")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        ReflectionTestUtils.setField(
                report,
                "storagePath",
                "reports/v1/area/test_2026-07-24_10.md"
        );
        when(reportRepository.findById(10L)).thenReturn(Optional.of(report));

        reportService.delete(10L);

        InOrder order = inOrder(reportStorage, persistenceService);
        order.verify(reportStorage).delete(report.getStoragePath());
        order.verify(persistenceService).deleteMetadata(10L);
    }
}
