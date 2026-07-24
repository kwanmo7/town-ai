package com.townai.line.repository;

import com.townai.line.entity.LineVisitDraftEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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
     * 확인·취소 Transaction 동안 Draft와 연결 결과를 다른 요청이 변경하지 못하게
     * 잠근다.
     *
     * @param draftId 확인 또는 취소할 Draft ID
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
