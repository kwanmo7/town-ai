package com.townai.report.reuse;

import com.townai.area.repository.AreaRepository;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportGenerationData;
import com.townai.report.repository.ReportAreaRepository;
import com.townai.report.repository.ReportRepository;
import com.townai.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportReuseServiceTest {

    private static final String FINGERPRINT = "a".repeat(64);

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportAreaRepository reportAreaRepository;

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private VisitRepository visitRepository;

    private ReportReuseService reuseService;

    @BeforeEach
    void setUp() {
        reuseService = new ReportReuseService(
                reportRepository,
                reportAreaRepository,
                areaRepository,
                visitRepository
        );
    }

    @Test
    void returnsReportWithExactSourceFingerprint() {
        ReportEntity report = report(10L, FINGERPRINT);
        when(reportRepository
                .findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                        ReportType.SUMMARY,
                        "summary-v1",
                        FINGERPRINT
                )).thenReturn(Optional.of(report));

        Optional<ReportEntity> result = reuseService.findReusable(
                summaryData(),
                FINGERPRINT
        );

        assertEquals(10L, result.orElseThrow().getId());
        verifyNoInteractions(
                reportAreaRepository,
                areaRepository,
                visitRepository
        );
    }

    @Test
    void assignsFingerprintToUnchangedLegacyReport() {
        ReportEntity report = report(10L, null);
        when(reportRepository
                .findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                        ReportType.SUMMARY,
                        "summary-v1",
                        FINGERPRINT
                )).thenReturn(Optional.empty());
        when(reportRepository
                .findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                        ReportType.SUMMARY,
                        "summary-v1"
                )).thenReturn(List.of(report));
        when(reportAreaRepository.findAllWithAreaByReportId(10L))
                .thenReturn(List.of());
        when(areaRepository.existsChangedAfter(report.getCreatedAt()))
                .thenReturn(false);
        when(visitRepository.existsByUpdatedAtAfter(report.getCreatedAt()))
                .thenReturn(false);
        when(reportRepository.save(report)).thenReturn(report);

        Optional<ReportEntity> result = reuseService.findReusable(
                summaryData(),
                FINGERPRINT
        );

        assertEquals(10L, result.orElseThrow().getId());
        assertEquals(FINGERPRINT, report.getSourceFingerprint());
        verify(reportRepository).save(report);
    }

    @Test
    void rejectsLegacyReportWhenAreaWasAddedAfterGeneration() {
        ReportEntity report = report(10L, null);
        when(reportRepository
                .findFirstByReportTypeAndPromptVersionAndSourceFingerprintOrderByCreatedAtDescIdDesc(
                        ReportType.SUMMARY,
                        "summary-v1",
                        FINGERPRINT
                )).thenReturn(Optional.empty());
        when(reportRepository
                .findAllByReportTypeAndPromptVersionAndSourceFingerprintIsNullOrderByCreatedAtDescIdDesc(
                        ReportType.SUMMARY,
                        "summary-v1"
                )).thenReturn(List.of(report));
        when(reportAreaRepository.findAllWithAreaByReportId(10L))
                .thenReturn(List.of());
        when(areaRepository.existsChangedAfter(report.getCreatedAt()))
                .thenReturn(true);

        Optional<ReportEntity> result = reuseService.findReusable(
                summaryData(),
                FINGERPRINT
        );

        assertTrue(result.isEmpty());
        verify(reportRepository, never()).save(report);
        verifyNoInteractions(visitRepository);
    }

    private ReportGenerationData summaryData() {
        return new ReportGenerationData(
                ReportType.SUMMARY,
                List.of(),
                new Object()
        );
    }

    private ReportEntity report(Long id, String sourceFingerprint) {
        ReportEntity report = ReportEntity.builder()
                .reportType(ReportType.SUMMARY)
                .model("test-model")
                .promptVersion("summary-v1")
                .sourceFingerprint(sourceFingerprint)
                .build();
        ReflectionTestUtils.setField(report, "id", id);
        ReflectionTestUtils.setField(
                report,
                "createdAt",
                Instant.parse("2026-08-05T01:02:03Z")
        );
        return report;
    }
}
