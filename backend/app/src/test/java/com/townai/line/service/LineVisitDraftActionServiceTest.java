package com.townai.line.service;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.area.service.AreaService;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import com.townai.visit.service.VisitService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineVisitDraftActionServiceTest {

    private static final Instant NOW =
            Instant.parse("2026-07-25T10:00:00Z");
    private static final ZoneId TOKYO = ZoneId.of("Asia/Tokyo");

    private final LineVisitDraftRepository draftRepository =
            mock(LineVisitDraftRepository.class);
    private final AreaRepository areaRepository =
            mock(AreaRepository.class);
    private final AreaService areaService = mock(AreaService.class);
    private final VisitRepository visitRepository =
            mock(VisitRepository.class);
    private final VisitService visitService = mock(VisitService.class);
    private final FirestoreTransactionRunner transactions =
            mock(FirestoreTransactionRunner.class);
    private final LineVisitDraftActionService service =
            new LineVisitDraftActionService(
                    draftRepository,
                    areaRepository,
                    areaService,
                    visitRepository,
                    visitService,
                    transactions,
                    Clock.fixed(NOW, ZoneOffset.UTC),
                    TOKYO
            );

    @BeforeEach
    @SuppressWarnings("unchecked")
    void executeTransactionWorkImmediately() {
        when(transactions.execute(any(Supplier.class))).thenAnswer(invocation ->
                invocation.<Supplier<Object>>getArgument(0).get()
        );
    }

    @Test
    void confirmsDraftAndCreatesVisitInOneServiceCall() {
        AreaEntity area = area();
        LineVisitDraftEntity draft = completeDraft(area);
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));
        when(visitService.create(any())).thenReturn(
                mutationResponse(100L)
        );

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "user-1"
        );

        assertEquals(
                LineMessagePurpose.CONFIRM_RESULT,
                result.purpose()
        );
        assertEquals(
                "방문 기록을 저장했습니다. Visit ID: 100",
                result.message()
        );
        assertEquals(true, result.visitSaved());
        assertEquals(LineVisitDraftStatus.CONFIRMED, draft.getStatus());
        assertEquals(100L, draft.getConfirmedVisit().getId());
        verify(visitService).create(any());
    }

    @Test
    void repeatedConfirmationReturnsExistingVisitWithoutDuplicate() {
        AreaEntity area = area();
        LineVisitDraftEntity draft = completeDraft(area);
        VisitEntity visit = mock(VisitEntity.class);
        when(visit.getId()).thenReturn(100L);
        draft.confirm(visit);
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));
        clearInvocations(visitService);

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "user-1"
        );

        assertEquals(
                "방문 기록을 저장했습니다. Visit ID: 100",
                result.message()
        );
        assertEquals(true, result.visitSaved());
        verify(visitService, never()).create(any());
    }

    @Test
    void cancelsAwaitingDraftIdempotently() {
        LineVisitDraftEntity draft = completeDraft(area());
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));
        LineDraftCommand command = new LineDraftCommand(
                LineDraftAction.CANCEL,
                42L
        );

        LineDraftActionResult first =
                service.execute(command, "user-1");
        LineDraftActionResult second =
                service.execute(command, "user-1");

        assertEquals(first, second);
        assertEquals(
                LineMessagePurpose.CANCEL_RESULT,
                first.purpose()
        );
        assertEquals(LineVisitDraftStatus.CANCELLED, draft.getStatus());
        verify(visitService, never()).create(any());
    }

    @Test
    void requestsPartialRevisionWithoutSavingVisit() {
        LineVisitDraftEntity draft = completeDraft(area());
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.EDIT, 42L),
                "user-1"
        );

        assertEquals(LineMessagePurpose.EDIT_RESULT, result.purpose());
        assertEquals(
                LineVisitDraftStatus.AWAITING_REVISION,
                draft.getStatus()
        );
        assertEquals(true, result.message().contains("접근성 8로 수정"));
        verify(visitService, never()).create(any());
    }

    @Test
    void expiresDraftBeforeConfirmation() {
        LineVisitDraftEntity draft = completeDraft(area());
        ReflectionTestUtils.setField(
                draft,
                "expiresAt",
                NOW.minusSeconds(1)
        );
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "user-1"
        );

        assertEquals(LineVisitDraftStatus.EXPIRED, draft.getStatus());
        assertEquals(
                "확인 시간이 만료되었습니다. 자연어 평가를 다시 보내주세요.",
                result.message()
        );
        verify(visitService, never()).create(any());
    }

    @Test
    void rejectsDifferentDraftOwnerWithoutRevealingDraft() {
        LineVisitDraftEntity draft = completeDraft(area());
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "different-user"
        );

        assertEquals(
                "처리할 수 없는 방문 기록 초안입니다.",
                result.message()
        );
        assertEquals(
                LineVisitDraftStatus.AWAITING_CONFIRMATION,
                draft.getStatus()
        );
        verify(visitService, never()).create(any());
    }

    private LineVisitDraftEntity completeDraft(AreaEntity area) {
        VisitDraftResponse response = new VisitDraftResponse(
                existingAreaResponse(),
                LocalDate.parse("2026-07-24"),
                8,
                9,
                7,
                6,
                9,
                "걷기 편했음",
                List.of()
        );
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-1",
                "user-1",
                area,
                false,
                response,
                response.warnings(),
                NOW
        );
        ReflectionTestUtils.setField(draft, "id", 42L);
        return draft;
    }

    @Test
    void createsNewAreaAndVisitWhenCandidateIsConfirmed() {
        VisitDraftResponse response = new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        null,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                LocalDate.parse("2026-07-24"),
                8,
                9,
                7,
                6,
                9,
                "걷기 편했음",
                List.of("위치 정보를 확인해주세요.")
        );
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-new-area",
                "user-1",
                null,
                true,
                response,
                response.warnings(),
                NOW
        );
        ReflectionTestUtils.setField(draft, "id", 42L);
        AreaEntity createdArea = area();
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));
        when(areaRepository
                .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                        "가나가와현",
                        "요코하마시",
                        "센터미나미"
                )).thenReturn(Optional.empty());
        when(areaService.create(any())).thenReturn(new AreaDetailResponse(
                1L,
                "센터미나미",
                "가나가와현",
                "요코하마시",
                "센터미나미역",
                NOW,
                NOW
        ));
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(createdArea));
        when(visitService.create(any())).thenReturn(mutationResponse(100L));

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "user-1"
        );

        assertEquals(
                "방문 기록을 저장했습니다. Visit ID: 100",
                result.message()
        );
        assertEquals(true, result.visitSaved());
        assertEquals(createdArea, draft.getArea());
        assertEquals(false, draft.isAreaRegistrationRequired());
        verify(areaService).create(any());
        verify(visitService).create(any());
    }

    @Test
    void usesAreaCreatedByConcurrentConfirmation() {
        VisitDraftResponse response = new VisitDraftResponse(
                new VisitDraftAreaResponse(
                        null,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                LocalDate.parse("2026-07-24"),
                8,
                9,
                7,
                6,
                9,
                "걷기 편했음",
                List.of()
        );
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                "event-concurrent-area",
                "user-1",
                null,
                true,
                response,
                response.warnings(),
                NOW
        );
        ReflectionTestUtils.setField(draft, "id", 42L);
        AreaEntity concurrentlyCreatedArea = area();
        when(draftRepository.findByIdForUpdate(42L))
                .thenReturn(Optional.of(draft));
        when(areaRepository
                .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                        "가나가와현",
                        "요코하마시",
                        "센터미나미"
                ))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(concurrentlyCreatedArea));
        when(areaService.create(any())).thenThrow(
                new ApiException(ErrorCode.AREA_ALREADY_EXISTS)
        );
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(concurrentlyCreatedArea));
        when(visitService.create(any())).thenReturn(mutationResponse(100L));

        LineDraftActionResult result = service.execute(
                new LineDraftCommand(LineDraftAction.CONFIRM, 42L),
                "user-1"
        );

        assertEquals(true, result.visitSaved());
        assertEquals(concurrentlyCreatedArea, draft.getArea());
        verify(visitService).create(any());
    }

    private VisitDraftAreaResponse existingAreaResponse() {
        return new VisitDraftAreaResponse(
                1L,
                "센터미나미",
                "가나가와현",
                "요코하마시",
                "센터미나미역"
        );
    }

    private AreaEntity area() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        ReflectionTestUtils.setField(area, "id", 1L);
        return area;
    }

    private VisitMutationResponse mutationResponse(Long id) {
        return new VisitMutationResponse(
                id,
                null,
                LocalDate.parse("2026-07-24"),
                8,
                9,
                7,
                6,
                9,
                "걷기 편했음",
                NOW,
                NOW
        );
    }
}
