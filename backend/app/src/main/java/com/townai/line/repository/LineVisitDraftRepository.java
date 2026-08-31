package com.townai.line.repository;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** LINE Visit Draft의 상태·수정 Revision·만료 조회를 추상화하는 영속성 경계이다. */
public interface LineVisitDraftRepository {

    /**
     * Visit Draft의 현재 상태와 Revision을 저장한다.
     *
     * @param draft 저장할 Draft
     * @return ID와 저장 시각이 반영된 Draft
     */
    LineVisitDraftEntity save(LineVisitDraftEntity draft);

    /**
     * Visit Draft 여러 건을 같은 작업 흐름에서 저장한다.
     *
     * @param drafts 저장할 Draft 목록
     * @return 저장된 Draft 목록
     */
    List<LineVisitDraftEntity> saveAll(List<LineVisitDraftEntity> drafts);

    /**
     * 같은 원본 Webhook Event에서 생성된 Draft를 조회해 중복 생성을 막는다.
     *
     * @param sourceWebhookEventId 원본 LINE Event ID
     * @return 기존 Draft
     */
    Optional<LineVisitDraftEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    /**
     * 사용자에게 현재 이어서 처리할 수 있는 가장 최근 Draft를 반환한다.
     *
     * @param lineUserId LINE 사용자 ID
     * @param status 조회할 Draft 상태
     * @param currentTime 만료 판단 기준 시각
     * @return 이어서 처리할 수 있는 최근 Draft
     */
    Optional<LineVisitDraftEntity>
            findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                    String lineUserId,
                    LineVisitDraftStatus status,
                    Instant currentTime
            );

    /**
     * 사용자의 특정 상태 Draft를 조회한다.
     *
     * @param lineUserId LINE 사용자 ID
     * @param status 조회할 상태
     * @return 조건에 맞는 Draft 목록
     */
    List<LineVisitDraftEntity> findAllByLineUserIdAndStatus(
            String lineUserId,
            LineVisitDraftStatus status
    );

    /**
     * 사용자의 여러 상태 Draft를 조회한다.
     *
     * @param lineUserId LINE 사용자 ID
     * @param statuses 조회할 상태 목록
     * @return 조건에 맞는 Draft 목록
     */
    List<LineVisitDraftEntity> findAllByLineUserIdAndStatusIn(
            String lineUserId,
            List<LineVisitDraftStatus> statuses
    );

    /**
     * Transaction 안에서 수정할 Draft를 조회한다.
     *
     * @param draftId Draft ID
     * @return 존재하는 Draft
     */
    Optional<LineVisitDraftEntity> findByIdForUpdate(Long draftId);

    /**
     * 보존 기간이 지난 Draft 문서를 정리한다.
     *
     * @param cutoff 보존 기준 시각
     * @return 삭제한 Draft 수
     */
    int deleteCreatedBefore(Instant cutoff);
}
