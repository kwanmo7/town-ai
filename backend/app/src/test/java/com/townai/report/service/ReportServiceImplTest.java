package com.townai.report.service;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportResponse;
import com.townai.report.generation.ReportContentGenerator;
import com.townai.report.generation.ReportDataAssembler;
import com.townai.report.generation.ReportGenerationData;
import com.townai.report.persistence.ReportPersistenceService;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.report.reuse.ReportReuseService;
import com.townai.report.reuse.ReportSourceFingerprint;
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
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
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

    @Mock
    private ReportSourceFingerprint sourceFingerprint;

    @Mock
    private ReportReuseService reuseService;

    private ReportService reportService;

    @BeforeEach
    void setUp() {
        reportService = new ReportServiceImpl(
                dataAssembler,
                contentGenerator,
                persistenceService,
                reportRepository,
                reportAreaRepository,
                reportStorage,
                sourceFingerprint,
                reuseService
        );
    }

    @Test
    void reusesReportCreatedBySameLineWebhookEvent() {
        ReportEntity report = ReportEntity.builder()
                .reportType(ReportType.SUMMARY)
                .model("test-model")
                .promptVersion("summary-v1")
                .sourceWebhookEventId("event-1")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        ReflectionTestUtils.setField(
                report,
                "createdAt",
                Instant.parse("2026-08-03T01:02:03Z")
        );
        when(reportRepository.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.of(report));

        ReportResponse result = reportService.createForLine(
                new ReportCreateRequest(),
                "event-1"
        );

        assertEquals(10L, result.id());
        verifyNoInteractions(
                dataAssembler,
                contentGenerator,
                persistenceService
        );
    }

    @Test
    void findsExistingLineReportWithoutGenerating() {
        ReportEntity report = ReportEntity.builder()
                .reportType(ReportType.ALL)
                .model("test-model")
                .promptVersion("all-v1")
                .sourceWebhookEventId("event-1")
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        ReflectionTestUtils.setField(
                report,
                "createdAt",
                Instant.parse("2026-08-03T01:02:03Z")
        );
        when(reportRepository.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.of(report));

        Optional<ReportResponse> result =
                reportService.findBySourceWebhookEventId("event-1");

        assertEquals(10L, result.orElseThrow().id());
        verifyNoInteractions(
                dataAssembler,
                contentGenerator,
                persistenceService
        );
    }

    @Test
    void reusesSameReportInputForNewLineWebhookEvent() {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setReportType("SUMMARY");
        ReportGenerationData data = new ReportGenerationData(
                ReportType.SUMMARY,
                java.util.List.of(),
                new Object()
        );
        ReportEntity report = ReportEntity.builder()
                .reportType(ReportType.SUMMARY)
                .model("test-model")
                .promptVersion("summary-v1")
                .sourceFingerprint("a".repeat(64))
                .build();
        ReflectionTestUtils.setField(report, "id", 10L);
        ReflectionTestUtils.setField(
                report,
                "createdAt",
                Instant.parse("2026-08-03T01:02:03Z")
        );
        when(reportRepository.findBySourceWebhookEventId("event-2"))
                .thenReturn(Optional.empty());
        when(dataAssembler.prepare(request)).thenReturn(data);
        when(sourceFingerprint.calculate(data))
                .thenReturn("a".repeat(64));
        when(reuseService.findReusable(data, "a".repeat(64)))
                .thenReturn(Optional.of(report));

        ReportResponse result = reportService.createForLine(
                request,
                "event-2"
        );

        assertEquals(10L, result.id());
        verifyNoInteractions(contentGenerator, persistenceService);
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
