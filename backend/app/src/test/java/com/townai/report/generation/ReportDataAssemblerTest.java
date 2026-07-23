package com.townai.report.generation;

import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.entity.ReportType;
import com.townai.statistics.model.OverallStatistics;
import com.townai.statistics.model.ScoreAverages;
import com.townai.statistics.model.TopArea;
import com.townai.statistics.model.TopFive;
import com.townai.statistics.service.StatisticsService;
import com.townai.visit.repository.VisitRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportDataAssemblerTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private StatisticsService statisticsService;

    private ReportDataAssembler assembler;

    @BeforeEach
    void setUp() {
        assembler = new ReportDataAssembler(
                areaRepository,
                visitRepository,
                statisticsService
        );
    }

    @Test
    void rejectsAreaIdsFieldForSummaryEvenWhenExplicitlyNull() {
        ReportCreateRequest request = request("SUMMARY");
        request.setAreaIds(null);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> assembler.prepare(request)
        );

        assertEquals(ErrorCode.INVALID_REPORT_TARGETS, exception.errorCode());
        verify(areaRepository, never()).findAllByDeletedAtIsNullOrderByIdAsc();
    }

    @Test
    void rejectsDuplicateCompareTargets() {
        ReportCreateRequest request = request("COMPARE");
        request.setAreaIds(List.of(1L, 1L));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> assembler.prepare(request)
        );

        assertEquals(ErrorCode.INVALID_REPORT_TARGETS, exception.errorCode());
        verify(areaRepository, never()).findByIdAndDeletedAtIsNull(1L);
    }

    @Test
    void rejectsMissingOrDeletedAreaAsNotFound() {
        ReportCreateRequest request = request("AREA");
        request.setAreaIds(List.of(99L));

        ApiException exception = assertThrows(
                ApiException.class,
                () -> assembler.prepare(request)
        );

        assertEquals(ErrorCode.AREA_NOT_FOUND, exception.errorCode());
    }

    @Test
    void reusesStatisticsResultForSummaryPromptInput() {
        List<TopArea> atmosphereTop = List.of(
                new TopArea(1L, "A", 8.0),
                new TopArea(2L, "B", 8.0)
        );
        when(statisticsService.getOverallStatistics()).thenReturn(
                new OverallStatistics(
                        2,
                        3,
                        new ScoreAverages(8.0, 8.0, 8.0, 8.0, 8.0),
                        new TopFive(
                                atmosphereTop,
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()
                        )
                )
        );

        ReportGenerationData result = assembler.prepare(request("SUMMARY"));
        ReportDataAssembler.SummaryInput input =
                (ReportDataAssembler.SummaryInput) result.promptInput();

        assertEquals(ReportType.SUMMARY, result.reportType());
        assertEquals(2, input.areaCount());
        assertEquals(3, input.visitCount());
        assertEquals(8.0, input.averageScores().atmosphere());
        assertEquals(
                List.of(1L, 2L),
                input.top5().atmosphere().stream()
                        .map(ReportDataAssembler.TopArea::areaId)
                        .toList()
        );
    }

    private ReportCreateRequest request(String reportType) {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setReportType(reportType);
        return request;
    }

}
