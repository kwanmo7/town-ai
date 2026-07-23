package com.townai.visit.repository;

import com.townai.visit.entity.VisitEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Visit 영속성 처리와 API·Report용 조회 Query를 제공한다.
 *
 * <p>통계 집계 Query는 CRUD 책임과 분리해
 * {@link com.townai.statistics.repository.StatisticsRepository}에서 관리한다.</p>
 */
public interface VisitRepository extends JpaRepository<VisitEntity, Long> {

    /**
     * 상세 응답에서 Area를 사용할 수 있도록 Visit과 Area를 함께 조회한다.
     *
     * @param id 조회할 Visit ID
     * @return Visit과 연결된 Area. Visit이 없으면 빈 Optional
     */
    @Override
    @EntityGraph(attributePaths = "area")
    Optional<VisitEntity> findById(Long id);

    /**
     * 전달된 조건만 적용하고 방문일 및 ID 내림차순으로 조회한다.
     *
     * <p>삭제된 Area ID가 필터로 지정되면 빈 목록을 반환하지만, Area 필터가 없으면
     * 원본 기록 보존을 위해 삭제된 Area의 기존 Visit도 조회한다.</p>
     *
     * @param areaId 활성 Area 필터. {@code null}이면 전체 Area
     * @param fromDate 포함되는 방문일 하한
     * @param toDate 포함되는 방문일 상한
     * @return 방문일 내림차순, 동률이면 ID 내림차순인 Visit 목록
     */
    @Query("""
            SELECT v
            FROM VisitEntity v
            JOIN FETCH v.area a
            WHERE (:areaId IS NULL OR (a.id = :areaId AND a.deletedAt IS NULL))
              AND (:fromDate IS NULL OR v.visitDate >= :fromDate)
              AND (:toDate IS NULL OR v.visitDate <= :toDate)
            ORDER BY v.visitDate DESC, v.id DESC
            """)
    List<VisitEntity> findAllByFilters(
            @Param("areaId") Long areaId,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate
    );

    /**
     * ALL Report 입력에 사용할 활성 Area의 Visit을 조회한다.
     *
     * @return Area ID, 방문일, Visit ID 오름차순으로 정렬된 Visit 목록
     */
    @Query("""
            SELECT v
            FROM VisitEntity v
            JOIN FETCH v.area a
            WHERE a.deletedAt IS NULL
            ORDER BY a.id ASC, v.visitDate ASC, v.id ASC
            """)
    List<VisitEntity> findAllForActiveAreas();

    /**
     * AREA 또는 COMPARE Report의 선택 Area에 속한 Visit을 조회한다.
     *
     * <p>호출 전에 대상 Area의 존재와 활성 상태를 검증하므로 이 Query는 삭제 상태를
     * 다시 제한하지 않고 원본 Visit을 반환한다.</p>
     *
     * @param areaIds Report 생성 대상 Area ID 목록
     * @return Area ID, 방문일, Visit ID 오름차순으로 정렬된 Visit 목록
     */
    @Query("""
            SELECT v
            FROM VisitEntity v
            JOIN FETCH v.area a
            WHERE a.id IN :areaIds
            ORDER BY a.id ASC, v.visitDate ASC, v.id ASC
            """)
    List<VisitEntity> findAllByAreaIdsForReport(
            @Param("areaIds") List<Long> areaIds
    );
}
