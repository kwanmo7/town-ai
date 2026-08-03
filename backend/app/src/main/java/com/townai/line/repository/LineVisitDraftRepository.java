package com.townai.line.repository;

import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineVisitDraftStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * LINE Visit Draft의 멱등 조회와 상태 저장을 담당한다.
 */
public interface LineVisitDraftRepository
        extends JpaRepository<LineVisitDraftEntity, Long> {

    /**
     * 같은 Webhook 이벤트로 이미 생성된 Draft와 표시용 Area를 조회한다.
     *
     * @param sourceWebhookEventId Draft 생성 원본 이벤트 ID
     * @return 기존 Draft
     */
    @EntityGraph(attributePaths = "area")
    Optional<LineVisitDraftEntity> findBySourceWebhookEventId(
            String sourceWebhookEventId
    );

    /**
     * 사용자가 가장 최근에 수정 입력을 요청한 유효 Draft를 조회한다.
     *
     * @param lineUserId Draft 소유 LINE User ID
     * @param status 수정 입력 대기 상태
     * @param currentTime 만료 시각의 제외 하한
     * @return Area Snapshot까지 포함한 최신 수정 대상 Draft
     */
    @EntityGraph(attributePaths = "area")
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<LineVisitDraftEntity>
            findFirstByLineUserIdAndStatusAndExpiresAtAfterOrderByUpdatedAtDescIdDesc(
                    String lineUserId,
                    LineVisitDraftStatus status,
                    Instant currentTime
            );

    /**
     * 한 사용자가 이미 열어 둔 수정 대기 Draft를 잠금과 함께 조회한다.
     *
     * @param lineUserId Draft 소유 LINE User ID
     * @param status 수정 입력 대기 상태
     * @return 현재 수정 입력을 기다리는 Draft 목록
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<LineVisitDraftEntity> findAllByLineUserIdAndStatus(
            String lineUserId,
            LineVisitDraftStatus status
    );

    /**
     * 한 사용자의 수정 대기·처리 Draft를 잠금과 함께 조회한다.
     *
     * @param lineUserId Draft 소유 LINE User ID
     * @param statuses 조회할 수정 상태
     * @return 선택한 상태에 해당하는 Draft
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<LineVisitDraftEntity> findAllByLineUserIdAndStatusIn(
            String lineUserId,
            List<LineVisitDraftStatus> statuses
    );

    /**
     * 저장·수정·취소 Transaction 동안 Draft와 연결 결과를 다른 요청이 변경하지 못하게
     * 잠근다.
     *
     * @param draftId 처리할 Draft ID
     * @return Area와 기존 확인 Visit을 포함한 잠긴 Draft
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT draft
            FROM LineVisitDraftEntity draft
            LEFT JOIN FETCH draft.area
            LEFT JOIN FETCH draft.confirmedVisit
            WHERE draft.id = :draftId
            """)
    Optional<LineVisitDraftEntity> findByIdForUpdate(
            @Param("draftId") Long draftId
    );

    /**
     * 보존 기간을 지난 Draft를 물리 삭제한다.
     *
     * <p>Draft에서 Visit을 참조하므로 확인된 Draft를 삭제해도 Visit은 삭제되지
     * 않는다. 원본 Webhook 이벤트보다 먼저 실행해 FK 참조를 제거한다.</p>
     *
     * @param cutoff 생성 시각의 제외 상한
     * @return 삭제한 Draft Row 수
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            DELETE FROM line_visit_draft
            WHERE created_at < :cutoff
            """, nativeQuery = true)
    int deleteCreatedBefore(@Param("cutoff") Instant cutoff);
}
