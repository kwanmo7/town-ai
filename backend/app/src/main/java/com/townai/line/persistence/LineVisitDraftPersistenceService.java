package com.townai.line.persistence;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.line.repository.LineWebhookEventRepository;
import com.townai.persistence.firestore.FirestoreTransactionRunner;
import com.townai.visit.dto.VisitDraftResponse;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * LINE Visit Draft 조회와 생성 Transaction을 분리해 제공한다.
 */
@Service
public class LineVisitDraftPersistenceService {

    private static final String INACTIVE_AREA_WARNING =
            "선택된 지역이 삭제되었거나 존재하지 않아 다시 입력해야 합니다.";
    private static final String NEW_AREA_WARNING =
            "등록되지 않은 지역입니다. 위치를 확인한 뒤 저장하면 지역과 방문 기록이 함께 등록됩니다.";
    private static final String INCOMPLETE_NEW_AREA_WARNING =
            "새 지역 등록에 필요한 도도부현 또는 시구정촌을 확인할 수 없습니다.";

    private final LineVisitDraftRepository draftRepository;
    private final LineWebhookEventRepository eventRepository;
    private final AreaRepository areaRepository;
    private final FirestoreTransactionRunner transactions;
    private final Clock clock;

    /**
     * LINE Draft 영속화 Service를 생성한다.
     *
     * @param draftRepository LINE Visit Draft Repository
     * @param eventRepository 수정 의도를 보존할 LINE Event Repository
     * @param areaRepository Parser 선택 Area 재검증 Repository
     * @param transactions Event·Draft를 함께 저장할 Transaction 실행기
     * @param clock Draft 만료 시각을 계산할 UTC Clock
     */
    public LineVisitDraftPersistenceService(
            LineVisitDraftRepository draftRepository,
            LineWebhookEventRepository eventRepository,
            AreaRepository areaRepository,
            FirestoreTransactionRunner transactions,
            Clock clock
    ) {
        this.draftRepository = draftRepository;
        this.eventRepository = eventRepository;
        this.areaRepository = areaRepository;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * 원본 이벤트로 이미 생성된 Draft를 조회한다.
     *
     * @param sourceWebhookEventId 원본 LINE Webhook Event ID
     * @return 기존 Draft
     */
    public Optional<LineVisitDraftEntity> findBySourceEventId(
            String sourceWebhookEventId
    ) {
        return draftRepository.findBySourceWebhookEventId(
                sourceWebhookEventId
        );
    }

    /**
     * 수정 Text Message가 사용할 원본 Draft를 AI 호출 전에 점유한다.
     *
     * <p>같은 Webhook Event의 재시도는 기존 점유를 다시 사용한다. 다른 Event가
     * 이미 수정 처리 중이면 추가 AI 호출을 막고 {@code BUSY}를 반환한다.</p>
     *
     * @param workItem 수정 가능성이 있는 Text Message 이벤트
     * @return 수정 대상 없음, 점유 성공, 다른 수정 처리 중 또는 무효 결과
     */
    public LineRevisionClaim claimRevision(
            LineWebhookEventWorkItem workItem
    ) {
        return transactions.execute(() -> claimRevisionInTransaction(workItem));
    }

    private LineRevisionClaim claimRevisionInTransaction(
            LineWebhookEventWorkItem workItem
    ) {
        LineWebhookEventEntity event = eventRepository
                .findByIdForUpdate(workItem.webhookEventId())
                .orElseThrow(() -> new IllegalStateException(
                        "Claimed LINE Event does not exist."
                ));
        if (event.getRevisionSourceDraftId() != null) {
            Optional<LineVisitDraftEntity> intendedSource = draftRepository
                    .findByIdForUpdate(event.getRevisionSourceDraftId());
            if (intendedSource.isPresent()
                    && intendedSource.get().getLineUserId().equals(
                            workItem.lineUserId()
                    )
                    && intendedSource.get().isRevisionClaimedBy(
                            workItem.webhookEventId()
                    )) {
                return LineRevisionClaim.claimed(intendedSource.get());
            }
            return LineRevisionClaim.invalid();
        }

        List<LineVisitDraftEntity> processing = draftRepository
                .findAllByLineUserIdAndStatus(
                        workItem.lineUserId(),
                        LineVisitDraftStatus.REVISION_PROCESSING
                );
        Instant now = clock.instant();
        LineVisitDraftEntity activeProcessing = null;
        List<LineVisitDraftEntity> changedDrafts = new ArrayList<>();
        for (LineVisitDraftEntity draft : processing) {
            if (now.isBefore(draft.getExpiresAt())) {
                if (activeProcessing == null) {
                    activeProcessing = draft;
                }
            } else {
                draft.expire();
                changedDrafts.add(draft);
            }
        }
        if (activeProcessing != null) {
            event.assignRevisionSource(activeProcessing.getId());
            draftRepository.saveAll(changedDrafts);
            eventRepository.save(event);
            return LineRevisionClaim.busy();
        }

        Optional<LineVisitDraftEntity> awaiting = draftRepository
                .findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                        workItem.lineUserId(),
                        LineVisitDraftStatus.AWAITING_REVISION,
                        now
                );
        if (awaiting.isEmpty()) {
            draftRepository.saveAll(changedDrafts);
            return LineRevisionClaim.none();
        }
        event.assignRevisionSource(awaiting.get().getId());
        awaiting.get().beginRevision(workItem.webhookEventId());
        changedDrafts.add(awaiting.get());
        draftRepository.saveAll(changedDrafts);
        eventRepository.save(event);
        return LineRevisionClaim.claimed(awaiting.get());
    }

    /**
     * Transaction 안에서 기존 Draft를 다시 확인하고 없을 때만 생성한다.
     *
     * @param workItem Text Message 이벤트 Snapshot
     * @param response Backend 검증을 통과한 Parser 응답
     * @return 기존 또는 새 LINE Draft
     */
    public LineVisitDraftEntity createIfAbsent(
            LineWebhookEventWorkItem workItem,
            VisitDraftResponse response
    ) {
        return transactions.execute(() -> createIfAbsentInTransaction(
                workItem,
                response
        ));
    }

    private LineVisitDraftEntity createIfAbsentInTransaction(
            LineWebhookEventWorkItem workItem,
            VisitDraftResponse response
    ) {
        Optional<LineVisitDraftEntity> existing =
                draftRepository.findBySourceWebhookEventId(
                        workItem.webhookEventId()
                );
        if (existing.isPresent()) {
            return existing.get();
        }

        LineVisitDraftEntity draft = createDraft(workItem, response);
        return draftRepository.save(draft);
    }

    /**
     * 수정 Text Message를 새 Draft로 저장하고 이전 Draft를 대체 상태로 전환한다.
     *
     * <p>새 Draft는 수정 Text Message의 Webhook Event ID를 사용하므로 Push 재시도
     * 시에도 중복 생성되지 않는다.</p>
     *
     * @param workItem 수정 내용을 담은 Text Message 이벤트
     * @param sourceDraftId 수정 버튼으로 선택한 원본 Draft ID
     * @param response 기존 값과 수정 내용을 병합한 Parser 응답
     * @return 이미 처리됐거나 새로 만든 수정 Draft. 점유가 무효면 빈 값
     */
    public Optional<LineVisitDraftEntity> createRevisionIfAbsent(
            LineWebhookEventWorkItem workItem,
            Long sourceDraftId,
            VisitDraftResponse response
    ) {
        return transactions.execute(() -> createRevisionIfAbsentInTransaction(
                workItem,
                sourceDraftId,
                response
        ));
    }

    private Optional<LineVisitDraftEntity> createRevisionIfAbsentInTransaction(
            LineWebhookEventWorkItem workItem,
            Long sourceDraftId,
            VisitDraftResponse response
    ) {
        Optional<LineVisitDraftEntity> existing =
                draftRepository.findBySourceWebhookEventId(
                        workItem.webhookEventId()
                );
        if (existing.isPresent()) {
            return existing;
        }

        Optional<LineVisitDraftEntity> foundSource =
                draftRepository.findByIdForUpdate(sourceDraftId);
        if (foundSource.isEmpty()
                || !foundSource.get().getLineUserId().equals(
                        workItem.lineUserId()
                )
                || foundSource.get().getStatus()
                != LineVisitDraftStatus.REVISION_PROCESSING
                || !foundSource.get().isRevisionClaimedBy(
                        workItem.webhookEventId()
                )
                || !clock.instant().isBefore(
                        foundSource.get().getExpiresAt()
                )) {
            return Optional.empty();
        }

        LineVisitDraftEntity revised = createDraft(workItem, response);
        foundSource.get().supersede();
        draftRepository.saveAll(List.of(
                revised,
                foundSource.get()
        ));
        return Optional.of(revised);
    }

    private LineVisitDraftEntity createDraft(
            LineWebhookEventWorkItem workItem,
            VisitDraftResponse response
    ) {
        AreaResolution areaResolution = resolveArea(response);
        List<String> warnings = new ArrayList<>(response.warnings());
        if (response.area() != null
                && response.area().registered()
                && areaResolution.area() == null) {
            warnings.add(INACTIVE_AREA_WARNING);
        }
        if (areaResolution.registrationRequired()) {
            addWarningIfAbsent(
                    warnings,
                    response.area().hasRequiredLocation()
                            ? NEW_AREA_WARNING
                            : INCOMPLETE_NEW_AREA_WARNING
            );
        }
        return LineVisitDraftEntity.create(
                workItem.webhookEventId(),
                workItem.lineUserId(),
                areaResolution.area(),
                areaResolution.registrationRequired(),
                response,
                List.copyOf(warnings),
                clock.instant()
        );
    }

    private AreaResolution resolveArea(VisitDraftResponse response) {
        if (response.area() == null) {
            return new AreaResolution(null, false);
        }
        if (response.area().registered()) {
            AreaEntity area = areaRepository.findByIdAndDeletedAtIsNull(
                    response.area().id()
            ).orElse(null);
            return new AreaResolution(area, false);
        }
        if (response.area().hasRequiredLocation()) {
            Optional<AreaEntity> existing = areaRepository
                    .findByPrefectureAndCityAndNameAndDeletedAtIsNull(
                            response.area().prefecture(),
                            response.area().city(),
                            response.area().name()
                    );
            if (existing.isPresent()) {
                return new AreaResolution(existing.get(), false);
            }
        }
        return new AreaResolution(null, true);
    }

    private void addWarningIfAbsent(
            List<String> warnings,
            String warning
    ) {
        if (!warnings.contains(warning)) {
            warnings.add(warning);
        }
    }

    private record AreaResolution(
            AreaEntity area,
            boolean registrationRequired
    ) {
    }
}
