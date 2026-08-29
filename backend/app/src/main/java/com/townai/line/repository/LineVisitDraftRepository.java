package com.townai.line.repository;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for LINE Visit Draft documents. */
public interface LineVisitDraftRepository {

    LineVisitDraftEntity save(LineVisitDraftEntity draft);

    List<LineVisitDraftEntity> saveAll(List<LineVisitDraftEntity> drafts);

    Optional<LineVisitDraftEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    Optional<LineVisitDraftEntity>
            findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                    String lineUserId,
                    LineVisitDraftStatus status,
                    Instant currentTime
            );

    List<LineVisitDraftEntity> findAllByLineUserIdAndStatus(
            String lineUserId,
            LineVisitDraftStatus status
    );

    List<LineVisitDraftEntity> findAllByLineUserIdAndStatusIn(
            String lineUserId,
            List<LineVisitDraftStatus> statuses
    );

    Optional<LineVisitDraftEntity> findByIdForUpdate(Long draftId);

    int deleteCreatedBefore(Instant cutoff);
}
