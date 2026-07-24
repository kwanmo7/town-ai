package com.townai.line.persistence;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.model.LineWebhookEventWorkItem;
import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.visit.dto.VisitDraftResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
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

    private final LineVisitDraftRepository draftRepository;
    private final AreaRepository areaRepository;
    private final Clock clock;

    /**
     * LINE Draft 영속화 Service를 생성한다.
     *
     * @param draftRepository LINE Visit Draft Repository
     * @param areaRepository Parser 선택 Area 재검증 Repository
     * @param clock Draft 만료 시각을 계산할 UTC Clock
     */
    public LineVisitDraftPersistenceService(
            LineVisitDraftRepository draftRepository,
            AreaRepository areaRepository,
            Clock clock
    ) {
        this.draftRepository = draftRepository;
        this.areaRepository = areaRepository;
        this.clock = clock;
    }

    /**
     * 원본 이벤트로 이미 생성된 Draft를 조회한다.
     *
     * @param sourceWebhookEventId 원본 LINE Webhook Event ID
     * @return 기존 Draft
     */
    @Transactional(readOnly = true)
    public Optional<LineVisitDraftEntity> findBySourceEventId(
            String sourceWebhookEventId
    ) {
        return draftRepository.findBySourceWebhookEventId(
                sourceWebhookEventId
        );
    }

    /**
     * Transaction 안에서 기존 Draft를 다시 확인하고 없을 때만 생성한다.
     *
     * @param workItem Text Message 이벤트 Snapshot
     * @param response Backend 검증을 통과한 Parser 응답
     * @return 기존 또는 새 LINE Draft
     */
    @Transactional
    public LineVisitDraftEntity createIfAbsent(
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

        AreaEntity area = resolveActiveArea(response);
        List<String> warnings = new ArrayList<>(response.warnings());
        if (response.area() != null && area == null) {
            warnings.add(INACTIVE_AREA_WARNING);
        }
        LineVisitDraftEntity draft = LineVisitDraftEntity.create(
                workItem.webhookEventId(),
                workItem.lineUserId(),
                area,
                response,
                List.copyOf(warnings),
                clock.instant()
        );
        return draftRepository.saveAndFlush(draft);
    }

    private AreaEntity resolveActiveArea(VisitDraftResponse response) {
        if (response.area() == null) {
            return null;
        }
        return areaRepository.findByIdAndDeletedAtIsNull(
                response.area().id()
        ).orElse(null);
    }
}
