package com.townai.line.persistence;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.repository.LineWebhookEventRepository;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineVisitDraftPersistenceServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-08-04T01:00:00Z");

    private final LineVisitDraftRepository draftRepository =
            mock(LineVisitDraftRepository.class);
    private final LineWebhookEventRepository eventRepository =
            mock(LineWebhookEventRepository.class);
    private final AreaRepository areaRepository = mock(AreaRepository.class);
    private final LineVisitDraftPersistenceService service =
            new LineVisitDraftPersistenceService(
                    draftRepository,
                    eventRepository,
                    areaRepository,
                    Clock.fixed(NOW, ZoneOffset.UTC)
            );

    @Test
    void storesCompleteUnregisteredAreaAsConfirmableCandidate() {
        VisitDraftResponse response = response(null);
        when(draftRepository.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.empty());
        when(areaRepository
                .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                        "가나가와현",
                        "요코하마시",
                        "센터미나미"
                )).thenReturn(Optional.empty());
        when(draftRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        LineVisitDraftEntity result = service.createIfAbsent(
                workItem(),
                response
        );

        assertNull(result.getArea());
        assertTrue(result.isAreaRegistrationRequired());
        assertEquals("센터미나미", result.getAreaName());
        assertEquals(
                LineVisitDraftStatus.AWAITING_CONFIRMATION,
                result.getStatus()
        );
        assertTrue(result.getWarnings().stream().anyMatch(
                warning -> warning.contains("지역과 방문 기록이 함께 등록")
        ));
    }

    @Test
    void reusesExistingAreaForCandidateWithSameLocation() {
        AreaEntity existing = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        VisitDraftResponse response = response(null);
        when(draftRepository.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.empty());
        when(areaRepository
                .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                        "가나가와현",
                        "요코하마시",
                        "센터미나미"
                )).thenReturn(Optional.of(existing));
        when(draftRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        LineVisitDraftEntity result = service.createIfAbsent(
                workItem(),
                response
        );

        assertEquals(existing, result.getArea());
        assertEquals(false, result.isAreaRegistrationRequired());
        ArgumentCaptor<LineVisitDraftEntity> captor =
                ArgumentCaptor.forClass(LineVisitDraftEntity.class);
        verify(draftRepository).saveAndFlush(captor.capture());
        assertEquals(existing, captor.getValue().getArea());
    }

    @Test
    void storesRevisedDraftAndSupersedesSourceAtomically() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        ReflectionTestUtils.setField(area, "id", 1L);
        LineVisitDraftEntity source = LineVisitDraftEntity.create(
                "event-original",
                "user-1",
                area,
                false,
                response(1L),
                List.of(),
                NOW
        );
        ReflectionTestUtils.setField(source, "id", 42L);
        source.requestRevision();
        source.beginRevision("event-1");
        VisitDraftResponse revised = new VisitDraftResponse(
                response(1L).area(),
                LocalDate.parse("2026-07-25"),
                8,
                8,
                8,
                7,
                9,
                "역 주변을 둘러봄",
                List.of()
        );
        when(draftRepository.findBySourceWebhookEventId("event-1"))
                .thenReturn(Optional.empty());
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(source));
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));
        when(draftRepository.saveAndFlush(
                org.mockito.ArgumentMatchers.any()
        )).thenAnswer(invocation -> invocation.getArgument(0));

        Optional<LineVisitDraftEntity> result =
                service.createRevisionIfAbsent(
                        workItem(),
                        42L,
                        revised
                );

        assertTrue(result.isPresent());
        assertEquals(9, result.get().getAccessScore());
        assertEquals(LineVisitDraftStatus.SUPERSEDED, source.getStatus());
    }

    @Test
    void claimsAwaitingRevisionBeforeAiCall() {
        LineWebhookEventEntity event = mock(LineWebhookEventEntity.class);
        LineVisitDraftEntity source = LineVisitDraftEntity.create(
                "event-original",
                "user-1",
                null,
                true,
                response(null),
                List.of(),
                NOW
        );
        source.requestRevision();
        ReflectionTestUtils.setField(source, "id", 42L);
        when(event.getRevisionSourceDraftId()).thenReturn(null);
        when(eventRepository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));
        when(draftRepository.findAllByLineUserIdAndStatus(
                "user-1",
                LineVisitDraftStatus.REVISION_PROCESSING
        )).thenReturn(List.of());
        when(draftRepository
                .findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                        "user-1",
                        LineVisitDraftStatus.AWAITING_REVISION,
                        NOW
                )).thenReturn(Optional.of(source));

        LineRevisionClaim result = service.claimRevision(workItem());

        assertEquals(LineRevisionClaim.Status.CLAIMED, result.status());
        assertEquals(source, result.source());
        assertEquals(
                LineVisitDraftStatus.REVISION_PROCESSING,
                source.getStatus()
        );
        assertEquals("event-1", source.getRevisionWebhookEventId());
        verify(event).assignRevisionSource(42L);
    }

    @Test
    void rejectsSecondCorrectionWhileRevisionIsProcessing() {
        LineWebhookEventEntity event = mock(LineWebhookEventEntity.class);
        LineVisitDraftEntity source = LineVisitDraftEntity.create(
                "event-original",
                "user-1",
                null,
                true,
                response(null),
                List.of(),
                NOW
        );
        source.requestRevision();
        source.beginRevision("event-other");
        ReflectionTestUtils.setField(source, "id", 42L);
        when(event.getRevisionSourceDraftId()).thenReturn(null);
        when(eventRepository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));
        when(draftRepository.findAllByLineUserIdAndStatus(
                "user-1",
                LineVisitDraftStatus.REVISION_PROCESSING
        )).thenReturn(List.of(source));

        LineRevisionClaim result = service.claimRevision(workItem());

        assertEquals(LineRevisionClaim.Status.BUSY, result.status());
        verify(event).assignRevisionSource(42L);
    }

    @Test
    void keepsRevisionIntentInvalidAfterSourceWasCancelled() {
        LineWebhookEventEntity event = mock(LineWebhookEventEntity.class);
        LineVisitDraftEntity source = LineVisitDraftEntity.create(
                "event-original",
                "user-1",
                null,
                true,
                response(null),
                List.of(),
                NOW
        );
        source.cancel();
        when(event.getRevisionSourceDraftId()).thenReturn(42L);
        when(eventRepository.findByIdForUpdate("event-1"))
                .thenReturn(Optional.of(event));
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(source));

        LineRevisionClaim result = service.claimRevision(workItem());

        assertEquals(LineRevisionClaim.Status.INVALID, result.status());
    }

    private VisitDraftResponse response(Long areaId) {
        return new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        areaId,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                LocalDate.parse("2026-07-25"),
                8,
                8,
                8,
                7,
                7,
                "역 주변을 둘러봄",
                List.of("위치 정보를 확인해주세요.")
        );
    }

    private LineWebhookEventWorkItem workItem() {
        return new LineWebhookEventWorkItem(
                "event-1",
                "user-1",
                LineWebhookEventType.TEXT_MESSAGE,
                "센터미나미를 방문했어.",
                null,
                NOW,
                1
        );
    }
}
