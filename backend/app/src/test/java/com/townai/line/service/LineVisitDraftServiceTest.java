package com.townai.line.service;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.persistence.LineVisitDraftPersistenceService;
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

        LineVisitDraftEntity result = service.getOrCreate(workItem());

        assertEquals(existing, result);
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
        when(visitDraftService.create(
                new VisitDraftRequest("센터미나미 방문")
        )).thenReturn(parserResponse);
        when(persistenceService.createIfAbsent(
                workItem,
                parserResponse
        )).thenReturn(saved);

        LineVisitDraftEntity result = service.getOrCreate(workItem);

        assertEquals(saved, result);
        verify(visitDraftService).create(
                new VisitDraftRequest("센터미나미 방문")
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
