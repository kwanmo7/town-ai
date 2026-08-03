package com.townai.line.service;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.dto.AreaRequest;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.area.service.AreaService;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import com.townai.line.messaging.LineMessagePurpose;
import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.dto.VisitRequest;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import com.townai.visit.service.VisitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * LINE Draft의 소유자·상태·만료·필수 값을 잠금 안에서 재검증하고 저장,
 * 수정 요청 또는 취소를 처리한다.
 */
@Service
public class LineVisitDraftActionService {

    private static final String AREA_REVALIDATION_WARNING =
            "지역이 삭제되었거나 신규 지역 정보를 등록할 수 없어 다시 입력해야 합니다.";
    private static final String VALUE_REVALIDATION_WARNING =
            "방문일 또는 점수 값이 유효하지 않아 다시 입력해야 합니다.";

    private final LineVisitDraftRepository draftRepository;
    private final AreaRepository areaRepository;
    private final AreaService areaService;
    private final VisitRepository visitRepository;
    private final VisitService visitService;
    private final Clock clock;
    private final ZoneId userTimeZone;

    /**
     * LINE Draft 저장·수정·취소 Service를 생성한다.
     *
     * @param draftRepository Draft Lock 및 상태 저장 Repository
     * @param areaRepository 활성 Area 재검증 Repository
     * @param areaService 신규 Area 등록 규칙을 재사용할 Service
     * @param visitRepository 확정된 Visit 관계 Reference 조회 Repository
     * @param visitService 기존 Visit 생성 규칙을 재사용할 Service
     * @param clock Draft 만료 판단 기준
     * @param userTimeZone 미래 방문일 검증에 사용할 생활권 시간대
     */
    public LineVisitDraftActionService(
            LineVisitDraftRepository draftRepository,
            AreaRepository areaRepository,
            AreaService areaService,
            VisitRepository visitRepository,
            VisitService visitService,
            Clock clock,
            ZoneId userTimeZone
    ) {
        this.draftRepository = draftRepository;
        this.areaRepository = areaRepository;
        this.areaService = areaService;
        this.visitRepository = visitRepository;
        this.visitService = visitService;
        this.clock = clock;
        this.userTimeZone = userTimeZone;
    }

    /**
     * Postback 사용자와 Draft를 검증하고 결과를 하나의 Transaction으로 반영한다.
     *
     * @param command 수정, 확인 또는 취소 명령
     * @param lineUserId Postback을 발생시킨 LINE User ID
     * @return 사용자에게 동일하게 재전송할 수 있는 처리 결과
     */
    @Transactional
    public LineDraftActionResult execute(
            LineDraftCommand command,
            String lineUserId
    ) {
        Optional<LineVisitDraftEntity> found =
                draftRepository.findByIdForUpdate(command.draftId());
        if (found.isEmpty()
                || !found.get().getLineUserId().equals(lineUserId)) {
            return result(
                    command.action(),
                    "처리할 수 없는 방문 기록 초안입니다."
            );
        }

        LineVisitDraftEntity draft = found.get();
        expireIfNecessary(draft);
        return switch (command.action()) {
            case CONFIRM -> confirm(draft);
            case EDIT -> requestRevision(draft);
            case CANCEL -> cancel(draft);
        };
    }

    private LineDraftActionResult confirm(LineVisitDraftEntity draft) {
        return switch (draft.getStatus()) {
            case CONFIRMED -> result(
                    LineDraftAction.CONFIRM,
                    confirmedMessage(draft)
            );
            case CANCELLED -> result(
                    LineDraftAction.CONFIRM,
                    "이미 취소된 방문 기록 초안입니다."
            );
            case EXPIRED -> result(
                    LineDraftAction.CONFIRM,
                    "확인 시간이 만료되었습니다. 자연어 평가를 다시 보내주세요."
            );
            case NEEDS_INPUT -> result(
                    LineDraftAction.CONFIRM,
                    "필수 값이 부족해 저장할 수 없습니다. 자연어 평가를 다시 보내주세요."
            );
            case AWAITING_REVISION -> result(
                    LineDraftAction.CONFIRM,
                    "수정 내용을 기다리고 있습니다. 변경할 내용만 보내주세요."
            );
            case REVISION_PROCESSING -> result(
                    LineDraftAction.CONFIRM,
                    "수정 내용을 처리 중입니다. 새 초안을 확인한 뒤 저장해주세요."
            );
            case SUPERSEDED -> result(
                    LineDraftAction.CONFIRM,
                    "수정된 새 방문 기록 초안이 이미 생성되었습니다."
            );
            case AWAITING_CONFIRMATION -> confirmAwaiting(draft);
        };
    }

    private LineDraftActionResult requestRevision(
            LineVisitDraftEntity draft
    ) {
        return switch (draft.getStatus()) {
            case AWAITING_CONFIRMATION, NEEDS_INPUT -> {
                supersedeOtherPendingRevisions(draft);
                draft.requestRevision();
                yield result(
                        LineDraftAction.EDIT,
                        revisionInstruction()
                );
            }
            case AWAITING_REVISION -> {
                supersedeOtherPendingRevisions(draft);
                yield result(
                        LineDraftAction.EDIT,
                        revisionInstruction()
                );
            }
            case REVISION_PROCESSING -> {
                supersedeOtherPendingRevisions(draft);
                draft.requestRevision();
                yield result(
                        LineDraftAction.EDIT,
                        revisionInstruction()
                );
            }
            case SUPERSEDED -> result(
                    LineDraftAction.EDIT,
                    "이미 수정된 초안입니다. 가장 최근 초안의 수정 버튼을 이용해주세요."
            );
            case CONFIRMED -> result(
                    LineDraftAction.EDIT,
                    "이미 저장된 방문 기록은 이 화면에서 수정할 수 없습니다."
            );
            case CANCELLED -> result(
                    LineDraftAction.EDIT,
                    "이미 취소된 방문 기록 초안입니다."
            );
            case EXPIRED -> result(
                    LineDraftAction.EDIT,
                    "수정 시간이 만료되었습니다. 방문 평가를 다시 보내주세요."
            );
        };
    }

    private LineDraftActionResult confirmAwaiting(
            LineVisitDraftEntity draft
    ) {
        AreaEntity area = resolveAreaForConfirmation(draft);
        if (area == null) {
            draft.requireNewInput(AREA_REVALIDATION_WARNING);
            return result(
                    LineDraftAction.CONFIRM,
                    AREA_REVALIDATION_WARNING
            );
        }
        if (!hasValidRequiredValues(draft)) {
            draft.requireNewInput(VALUE_REVALIDATION_WARNING);
            return result(
                    LineDraftAction.CONFIRM,
                    VALUE_REVALIDATION_WARNING
            );
        }

        VisitMutationResponse created = visitService.create(
                new VisitRequest(
                        area.getId(),
                        draft.getVisitDate(),
                        draft.getAtmosphereScore(),
                        draft.getInfraScore(),
                        draft.getCleanScore(),
                        draft.getSizeScore(),
                        draft.getAccessScore(),
                        draft.getMemo()
                )
        );
        VisitEntity visit = visitRepository.getReferenceById(created.id());
        draft.confirm(visit);
        return result(
                LineDraftAction.CONFIRM,
                "방문 기록을 저장했습니다. Visit ID: " + created.id()
        );
    }

    private LineDraftActionResult cancel(LineVisitDraftEntity draft) {
        return switch (draft.getStatus()) {
            case AWAITING_CONFIRMATION -> {
                draft.cancel();
                yield result(
                        LineDraftAction.CANCEL,
                        "방문 기록 초안을 취소했습니다."
                );
            }
            case CANCELLED -> result(
                    LineDraftAction.CANCEL,
                    "방문 기록 초안을 취소했습니다."
            );
            case CONFIRMED -> result(
                    LineDraftAction.CANCEL,
                    "이미 저장된 방문 기록은 취소할 수 없습니다."
            );
            case EXPIRED -> result(
                    LineDraftAction.CANCEL,
                    "이미 만료된 방문 기록 초안입니다."
            );
            case NEEDS_INPUT -> result(
                    LineDraftAction.CANCEL,
                    "확인 대기 중인 방문 기록 초안이 아닙니다."
            );
            case AWAITING_REVISION -> {
                draft.cancel();
                yield result(
                        LineDraftAction.CANCEL,
                        "방문 기록 초안을 취소했습니다."
                );
            }
            case REVISION_PROCESSING -> {
                draft.cancel();
                yield result(
                        LineDraftAction.CANCEL,
                        "방문 기록 초안을 취소했습니다. 처리 중인 수정 내용은 반영되지 않습니다."
                );
            }
            case SUPERSEDED -> result(
                    LineDraftAction.CANCEL,
                    "수정된 새 방문 기록 초안이 이미 생성되었습니다."
            );
        };
    }

    private void expireIfNecessary(LineVisitDraftEntity draft) {
        if ((draft.getStatus()
                == LineVisitDraftStatus.AWAITING_CONFIRMATION
                || draft.getStatus() == LineVisitDraftStatus.NEEDS_INPUT
                || draft.getStatus()
                == LineVisitDraftStatus.AWAITING_REVISION
                || draft.getStatus()
                == LineVisitDraftStatus.REVISION_PROCESSING)
                && !clock.instant().isBefore(draft.getExpiresAt())) {
            draft.expire();
        }
    }

    private void supersedeOtherPendingRevisions(
            LineVisitDraftEntity selectedDraft
    ) {
        draftRepository.findAllByLineUserIdAndStatusIn(
                selectedDraft.getLineUserId(),
                List.of(
                        LineVisitDraftStatus.AWAITING_REVISION,
                        LineVisitDraftStatus.REVISION_PROCESSING
                )
        ).stream()
                .filter(draft -> !draft.getId().equals(
                        selectedDraft.getId()
                ))
                .forEach(LineVisitDraftEntity::supersede);
    }

    private AreaEntity resolveAreaForConfirmation(
            LineVisitDraftEntity draft
    ) {
        if (draft.getArea() != null) {
            return areaRepository.findByIdAndDeletedAtIsNull(
                    draft.getArea().getId()
            ).orElse(null);
        }
        if (!draft.isAreaRegistrationRequired()
                || !hasText(draft.getAreaName())
                || !hasText(draft.getAreaPrefecture())
                || !hasText(draft.getAreaCity())) {
            return null;
        }

        Optional<AreaEntity> existing = areaRepository
                .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                        draft.getAreaPrefecture(),
                        draft.getAreaCity(),
                        draft.getAreaName()
                );
        if (existing.isPresent()) {
            draft.assignArea(existing.get());
            return existing.get();
        }
        if (areaRepository.existsByPrefectureAndCityAndName(
                draft.getAreaPrefecture(),
                draft.getAreaCity(),
                draft.getAreaName()
        )) {
            return null;
        }

        AreaDetailResponse created = areaService.create(new AreaRequest(
                draft.getAreaName(),
                draft.getAreaPrefecture(),
                draft.getAreaCity(),
                draft.getAreaStation()
        ));
        AreaEntity area = areaRepository.findByIdAndDeletedAtIsNull(
                created.id()
        ).orElseThrow();
        draft.assignArea(area);
        return area;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private boolean hasValidRequiredValues(
            LineVisitDraftEntity draft
    ) {
        LocalDate today = LocalDate.now(clock.withZone(userTimeZone));
        return draft.getVisitDate() != null
                && !draft.getVisitDate().isAfter(today)
                && validScore(draft.getAtmosphereScore())
                && validScore(draft.getInfraScore())
                && validScore(draft.getCleanScore())
                && validScore(draft.getSizeScore())
                && validScore(draft.getAccessScore());
    }

    private boolean validScore(Integer score) {
        return score != null && score >= 0 && score <= 10;
    }

    private String confirmedMessage(LineVisitDraftEntity draft) {
        Long visitId = draft.getConfirmedVisit() == null
                ? null
                : draft.getConfirmedVisit().getId();
        return visitId == null
                ? "방문 기록이 이미 저장되었습니다."
                : "방문 기록을 저장했습니다. Visit ID: " + visitId;
    }

    private LineDraftActionResult result(
            LineDraftAction action,
            String message
    ) {
        LineMessagePurpose purpose = switch (action) {
            case CONFIRM -> LineMessagePurpose.CONFIRM_RESULT;
            case EDIT -> LineMessagePurpose.EDIT_RESULT;
            case CANCEL -> LineMessagePurpose.CANCEL_RESULT;
        };
        return new LineDraftActionResult(purpose, message);
    }

    private String revisionInstruction() {
        return "수정할 내용만 입력해주세요. 예: '접근성 8로 수정', "
                + "'방문일은 7월 26일', '메모에 공원이 가까웠다고 추가'.";
    }
}
