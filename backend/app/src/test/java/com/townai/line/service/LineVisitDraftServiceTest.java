package com.townai.line.service;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.persistence.LineVisitDraftPersistenceService;
import com.townai.line.persistence.LineRevisionClaim;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.service.VisitDraftService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineVisitDraftServiceTest {

    private final LineVisitDraftPersistenceService persistenceService =
            mock(LineVisitDraftPersistenceService.class);
    private final VisitDraftService visitDraftService =
            mock(VisitDraftService.class);
    private final LineVisitDraftService service =
            new LineVisitDraftService(
                    persistenceService,
                    visitDraftService
            );

    @Test
    void reusesExistingDraftWithoutCallingParser() {
        LineVisitDraftEntity existing =
                mock(LineVisitDraftEntity.class);
        when(persistenceService.findBySourceEventId("event-1"))
                .thenReturn(Optional.of(existing));

        LineVisitDraftResult result = service.getOrCreate(workItem());

        assertEquals(existing, result.draft());
        verify(visitDraftService, never())
                .create(any(VisitDraftRequest.class));
        verify(persistenceService, never())
                .createIfAbsent(any(), any());
    }

    @Test
    void parsesAndPersistsNewDraft() {
        LineWebhookEventWorkItem workItem = workItem();
        VisitDraftResponse parserResponse = new VisitDraftResponse(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of("입력을 확인해주세요.")
        );
        LineVisitDraftEntity saved = mock(LineVisitDraftEntity.class);
        when(persistenceService.findBySourceEventId("event-1"))
                .thenReturn(Optional.empty());
        when(persistenceService.claimRevision(workItem))
                .thenReturn(LineRevisionClaim.none());
        when(visitDraftService.create(
                new VisitDraftRequest("센터미나미 방문")
        )).thenReturn(parserResponse);
        when(persistenceService.createIfAbsent(
                workItem,
                parserResponse
        )).thenReturn(saved);

        LineVisitDraftResult result = service.getOrCreate(workItem);

        assertEquals(saved, result.draft());
        verify(visitDraftService).create(
                new VisitDraftRequest("센터미나미 방문")
        );
    }

    @Test
    void mergesPartialRevisionIntoSelectedDraft() {
        LineWebhookEventWorkItem workItem = workItem();
        LineVisitDraftEntity source = mock(LineVisitDraftEntity.class);
        VisitDraftResponse existing = completeResponse(7);
        VisitDraftResponse revised = completeResponse(8);
        LineVisitDraftEntity saved = mock(LineVisitDraftEntity.class);
        when(source.getId()).thenReturn(42L);
        when(source.toDraftResponse()).thenReturn(existing);
        when(persistenceService.findBySourceEventId("event-1"))
                .thenReturn(Optional.empty());
        when(persistenceService.claimRevision(workItem))
                .thenReturn(LineRevisionClaim.claimed(source));
        when(visitDraftService.revise(
                existing,
                new VisitDraftRequest("센터미나미 방문")
        )).thenReturn(revised);
        when(persistenceService.createRevisionIfAbsent(
                workItem,
                42L,
                revised
        )).thenReturn(Optional.of(saved));

        LineVisitDraftResult result = service.getOrCreate(workItem);

        assertEquals(saved, result.draft());
        verify(visitDraftService).revise(
                existing,
                new VisitDraftRequest("센터미나미 방문")
        );
        verify(visitDraftService, never()).create(any());
    }

    @Test
    void doesNotCallParserWhileAnotherRevisionIsProcessing() {
        LineWebhookEventWorkItem workItem = workItem();
        when(persistenceService.findBySourceEventId("event-1"))
                .thenReturn(Optional.empty());
        when(persistenceService.claimRevision(workItem))
                .thenReturn(LineRevisionClaim.busy());

        LineVisitDraftResult result = service.getOrCreate(workItem);

        assertEquals(false, result.hasDraft());
        assertEquals(
                "수정 내용을 반영하지 않았습니다. 앞선 수정이 처리 중이거나 원본 초안 상태가 변경되었습니다. 최신 초안을 확인한 뒤 다시 수정해주세요.",
                result.notice()
        );
        verify(visitDraftService, never()).create(any());
        verify(visitDraftService, never()).revise(any(), any());
    }

    @Test
    void neverFallsBackToNewDraftWhenRevisionClaimBecomesInvalid() {
        LineWebhookEventWorkItem workItem = workItem();
        LineVisitDraftEntity source = mock(LineVisitDraftEntity.class);
        VisitDraftResponse existing = completeResponse(7);
        VisitDraftResponse revised = completeResponse(8);
        when(source.getId()).thenReturn(42L);
        when(source.toDraftResponse()).thenReturn(existing);
        when(persistenceService.findBySourceEventId("event-1"))
                .thenReturn(Optional.empty());
        when(persistenceService.claimRevision(workItem))
                .thenReturn(LineRevisionClaim.claimed(source));
        when(visitDraftService.revise(any(), any())).thenReturn(revised);
        when(persistenceService.createRevisionIfAbsent(
                workItem,
                42L,
                revised
        )).thenReturn(Optional.empty());

        LineVisitDraftResult result = service.getOrCreate(workItem);

        assertEquals(false, result.hasDraft());
        verify(persistenceService, never()).createIfAbsent(any(), any());
    }

    private VisitDraftResponse completeResponse(int accessScore) {
        return new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                java.time.LocalDate.parse("2026-07-25"),
                8,
                8,
                8,
                7,
                accessScore,
                "역 주변을 둘러봄",
                List.of()
        );
    }

    private LineWebhookEventWorkItem workItem() {
        return new LineWebhookEventWorkItem(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "센터미나미 방문",
                null,
                Instant.parse("2026-07-25T09:00:00Z"),
                1
        );
    }
}
