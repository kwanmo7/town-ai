package com.townai.line.service;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.persistence.LineVisitDraftPersistenceService;
import com.townai.line.persistence.LineRevisionClaim;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.service.VisitDraftService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * LINE Text Message를 기존 Visit Parser로 변환하고 멱등 Draft로 저장한다.
 *
 * <p>기존 Draft를 먼저 조회해 Webhook 재처리 시 OpenAI를 호출하지 않는다.
 * UNIQUE 충돌이 발생한 동시 처리에서도 최종적으로 먼저 저장된 Draft를 재사용한다.</p>
 */
@Service
public class LineVisitDraftService {

    private static final String REVISION_NOT_APPLIED_MESSAGE =
            "수정 내용을 반영하지 않았습니다. 앞선 수정이 처리 중이거나 원본 초안 상태가 변경되었습니다. 최신 초안을 확인한 뒤 다시 수정해주세요.";

    private final LineVisitDraftPersistenceService persistenceService;
    private final VisitDraftService visitDraftService;

    /**
     * LINE Draft Application Service를 생성한다.
     *
     * @param persistenceService LINE Draft 조회·저장 Transaction Service
     * @param visitDraftService Web API와 공통으로 사용하는 Visit Parser Service
     */
    public LineVisitDraftService(
            LineVisitDraftPersistenceService persistenceService,
            VisitDraftService visitDraftService
    ) {
        this.persistenceService = persistenceService;
        this.visitDraftService = visitDraftService;
    }

    /**
     * 기존 Draft를 반환하거나 Text Message를 파싱해 새 Draft를 저장한다.
     *
     * @param workItem 점유된 Text Message 이벤트
     * @return 같은 원본 이벤트에 하나만 존재하는 LINE Draft
     */
    public LineVisitDraftResult getOrCreate(
            LineWebhookEventWorkItem workItem
    ) {
        return persistenceService.findBySourceEventId(
                workItem.webhookEventId()
        ).map(LineVisitDraftResult::draft)
                .orElseGet(() -> processNewEvent(workItem));
    }

    private LineVisitDraftResult processNewEvent(
            LineWebhookEventWorkItem workItem
    ) {
        LineRevisionClaim claim = persistenceService.claimRevision(workItem);
        return switch (claim.status()) {
            case CLAIMED -> revise(workItem, claim.source());
            case BUSY, INVALID -> LineVisitDraftResult.notice(
                    REVISION_NOT_APPLIED_MESSAGE
            );
            case NONE -> LineVisitDraftResult.draft(create(workItem));
        };
    }

    private LineVisitDraftEntity create(
            LineWebhookEventWorkItem workItem
    ) {
        VisitDraftResponse response = visitDraftService.create(
                new VisitDraftRequest(workItem.messageText())
        );
        try {
            return persistenceService.createIfAbsent(workItem, response);
        } catch (DataIntegrityViolationException exception) {
            return persistenceService.findBySourceEventId(
                    workItem.webhookEventId()
            ).orElseThrow(() -> exception);
        }
    }

    private LineVisitDraftResult revise(
            LineWebhookEventWorkItem workItem,
            LineVisitDraftEntity source
    ) {
        VisitDraftResponse response = visitDraftService.revise(
                source.toDraftResponse(),
                new VisitDraftRequest(workItem.messageText())
        );
        try {
            return persistenceService.createRevisionIfAbsent(
                    workItem,
                    source.getId(),
                    response
            ).map(LineVisitDraftResult::draft)
                    .orElseGet(() -> LineVisitDraftResult.notice(
                            REVISION_NOT_APPLIED_MESSAGE
                    ));
        } catch (DataIntegrityViolationException exception) {
            return persistenceService.findBySourceEventId(
                    workItem.webhookEventId()
            ).map(LineVisitDraftResult::draft)
                    .orElseThrow(() -> exception);
        }
    }
}
