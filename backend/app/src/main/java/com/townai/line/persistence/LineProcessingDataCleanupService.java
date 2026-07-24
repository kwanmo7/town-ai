package com.townai.line.persistence;

import com.townai.line.repository.LineVisitDraftRepository;
import com.townai.line.repository.LineWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * 보존 기간을 지난 LINE Webhook Event와 Visit Draft를 기회적으로 정리한다.
 *
 * <p>FK 제약을 지키기 위해 Draft를 먼저 삭제한 뒤 참조가 없는 완료·실패
 * 이벤트만 삭제한다. 확인된 Draft가 참조한 Visit은 정리 대상이 아니다.</p>
 */
@Service
public class LineProcessingDataCleanupService {

    private static final Duration RETENTION_PERIOD =
            Duration.ofDays(30);

    private final LineVisitDraftRepository draftRepository;
    private final LineWebhookEventRepository eventRepository;
    private final Clock clock;

    /**
     * LINE 처리 데이터 Cleanup Service를 생성한다.
     *
     * @param draftRepository 보존 기간이 지난 Draft 삭제 Repository
     * @param eventRepository 완료·실패 Event 삭제 Repository
     * @param clock 30일 보존 기준을 계산할 UTC 시계
     */
    public LineProcessingDataCleanupService(
            LineVisitDraftRepository draftRepository,
            LineWebhookEventRepository eventRepository,
            Clock clock
    ) {
        this.draftRepository = draftRepository;
        this.eventRepository = eventRepository;
        this.clock = clock;
    }

    /**
     * 현재 시각보다 30일 이전에 생성된 처리 완료 데이터를 정리한다.
     *
     * @return Draft와 Webhook Event의 삭제 Row 수
     */
    @Transactional
    public LineCleanupResult cleanupExpiredData() {
        Instant cutoff = clock.instant()
                .minus(RETENTION_PERIOD)
                .truncatedTo(ChronoUnit.SECONDS);
        int deletedDrafts = draftRepository.deleteCreatedBefore(cutoff);
        int deletedEvents =
                eventRepository.deleteTerminalCreatedBeforeWithoutDraft(
                        cutoff
                );
        return new LineCleanupResult(deletedDrafts, deletedEvents);
    }
}
