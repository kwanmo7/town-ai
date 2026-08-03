package com.townai.line.service;

import com.townai.area.dto.AreaSummaryResponse;
import com.townai.area.entity.AreaEntity;
import com.townai.area.service.AreaService;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineReportMessageFactory;
import com.townai.line.model.LineReportAreaOption;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportType;
import com.townai.report.service.ReportService;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LineReportInteractionServiceTest {

    private final AreaService areaService = mock(AreaService.class);
    private final VisitRepository visitRepository =
            mock(VisitRepository.class);
    private final ReportService reportService = mock(ReportService.class);
    private final LineReportMessageFactory messageFactory =
            mock(LineReportMessageFactory.class);
    private final LineReportInteractionService service =
            new LineReportInteractionService(
                    areaService,
                    visitRepository,
                    reportService,
                    messageFactory
            );

    @Test
    void buildsAreaOptionsFromActiveAreasAndVisits() {
        AreaSummaryResponse area = new AreaSummaryResponse(
                1L,
                "센터미나미",
                "가나가와현",
                "요코하마시",
                "센터미나미역"
        );
        when(areaService.findAll()).thenReturn(List.of(area));
        List<VisitEntity> visits = List.of(
                visit(1L, "2026-07-20"),
                visit(1L, "2026-07-28")
        );
        when(visitRepository.findAllForActiveAreas()).thenReturn(visits);
        LinePushRequest expected = textRequest("지역 선택");
        when(messageFactory.createAreaSelection(
                org.mockito.ArgumentMatchers.eq("user-1"),
                org.mockito.ArgumentMatchers.anyList()
        )).thenReturn(expected);

        LinePushRequest result = service.createSelection(
                "user-1",
                ReportType.AREA
        );

        assertEquals(expected, result);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LineReportAreaOption>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(messageFactory).createAreaSelection(
                org.mockito.ArgumentMatchers.eq("user-1"),
                captor.capture()
        );
        LineReportAreaOption option = captor.getValue().getFirst();
        assertEquals(2, option.visitCount());
        assertEquals(LocalDate.parse("2026-07-28"), option.latestVisitDate());
    }

    @Test
    void generatesIdempotentCompareReportAndResultMessage() {
        LineReportGenerateCommand command = new LineReportGenerateCommand(
                ReportType.COMPARE,
                List.of(1L, 2L)
        );
        ReportResponse report = new ReportResponse(
                10L,
                ReportType.COMPARE,
                "test-model",
                "compare-v1",
                Instant.parse("2026-08-03T01:02:03Z")
        );
        when(areaService.findAll()).thenReturn(List.of(
                new AreaSummaryResponse(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        null
                ),
                new AreaSummaryResponse(
                        2L,
                        "무사시코스기",
                        "가나가와현",
                        "가와사키시",
                        null
                )
        ));
        when(reportService.createForLine(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq("event-1")
        )).thenReturn(report);
        LinePushRequest expected = textRequest("완료");
        when(messageFactory.createResult(
                "user-1",
                report,
                "센터미나미, 무사시코스기"
        )).thenReturn(expected);

        LinePushRequest result = service.generate(
                "user-1",
                "event-1",
                command
        );

        assertEquals(expected, result);
        ArgumentCaptor<ReportCreateRequest> requestCaptor =
                ArgumentCaptor.forClass(ReportCreateRequest.class);
        verify(reportService).createForLine(
                requestCaptor.capture(),
                org.mockito.ArgumentMatchers.eq("event-1")
        );
        assertEquals("COMPARE", requestCaptor.getValue().getReportType());
        assertEquals(List.of(1L, 2L), requestCaptor.getValue().getAreaIds());
    }

    @Test
    void restoresExistingReportBeforeCurrentDataValidation() {
        LineReportGenerateCommand command = new LineReportGenerateCommand(
                ReportType.ALL,
                List.of()
        );
        ReportResponse report = new ReportResponse(
                10L,
                ReportType.ALL,
                "test-model",
                "all-v1",
                Instant.parse("2026-08-03T01:02:03Z")
        );
        LinePushRequest expected = textRequest("기존 완료 결과");
        when(reportService.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.of(report));
        when(messageFactory.createResult(
                "user-1",
                report,
                "전체 지역"
        )).thenReturn(expected);

        Optional<LinePushRequest> result = service.findExistingResult(
                "user-1",
                "event-1",
                command
        );

        assertEquals(Optional.of(expected), result);
        verifyNoInteractions(visitRepository);
    }

    @Test
    void returnsUnavailableMessageWhenOverallReportHasNoVisits() {
        when(areaService.findAll()).thenReturn(List.of());
        when(visitRepository.findAllForActiveAreas()).thenReturn(List.of());
        LinePushRequest unavailable = textRequest("방문 기록 없음");
        when(messageFactory.createUnavailable(
                "user-1",
                "아직 등록된 방문 기록이 없습니다."
        )).thenReturn(unavailable);

        Optional<LinePushRequest> result = service.findUnavailableMessage(
                "user-1",
                new LineReportGenerateCommand(ReportType.ALL, List.of())
        );

        assertEquals(Optional.of(unavailable), result);
        verify(reportService, never()).createForLine(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString()
        );
    }

    private VisitEntity visit(long areaId, String date) {
        AreaEntity area = mock(AreaEntity.class);
        VisitEntity visit = mock(VisitEntity.class);
        when(area.getId()).thenReturn(areaId);
        when(visit.getArea()).thenReturn(area);
        when(visit.getVisitDate()).thenReturn(LocalDate.parse(date));
        return visit;
    }

    private LinePushRequest textRequest(String text) {
        return new LinePushRequest(
                "user-1",
                List.of(LinePushRequest.TextMessage.of(text))
        );
    }
}
