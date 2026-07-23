package com.townai.statistics.repository;

import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;
import com.townai.visit.entity.VisitEntity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Statistics 계산에 필요한 읽기 전용 JPQL 집계를 담당한다.
 *
 * <p>Visit CRUD Repository와 집계 책임을 분리하며, 모든 Query는 논리 삭제된 Area를
 * 제외한다. SQL에서 계산한 원시 평균은 Service에서 최종 반올림한다.</p>
 */
public interface StatisticsRepository extends Repository<VisitEntity, Long> {

    /**
     * SUMMARY에 사용할 전체 Visit 수와 점수 평균을 계산한다.
     *
     * @return 활성 Area에 속한 Visit 수와 항목별 원시 평균
     */
    @Query("""
            SELECT new com.townai.statistics.repository.projection.ScoreStatistics(
                COUNT(v),
                AVG(v.atmosphereScore),
                AVG(v.infraScore),
                AVG(v.cleanScore),
                AVG(v.sizeScore),
                AVG(v.accessScore)
            )
            FROM VisitEntity v
            JOIN v.area a
            WHERE a.deletedAt IS NULL
            """)
    ScoreStatistics summarizeActiveAreaVisits();

    /**
     * 삭제되지 않은 특정 Area의 Visit 수와 점수 평균을 계산한다.
     *
     * @param areaId 집계할 Area ID
     * @return 해당 Area의 Visit 수와 항목별 원시 평균
     */
    @Query("""
            SELECT new com.townai.statistics.repository.projection.ScoreStatistics(
                COUNT(v),
                AVG(v.atmosphereScore),
                AVG(v.infraScore),
                AVG(v.cleanScore),
                AVG(v.sizeScore),
                AVG(v.accessScore)
            )
            FROM VisitEntity v
            JOIN v.area a
            WHERE a.id = :areaId
              AND a.deletedAt IS NULL
            """)
    ScoreStatistics summarizeActiveAreaVisitsByAreaId(
            @Param("areaId") Long areaId
    );

    /**
     * SUMMARY Top 5의 기반이 되는 Area별 평균을 계산한다.
     *
     * @return Visit이 있는 활성 Area별 항목 평균. Area ID 오름차순
     */
    @Query("""
            SELECT new com.townai.statistics.repository.projection.AreaScoreStatistics(
                a.id,
                a.name,
                AVG(v.atmosphereScore),
                AVG(v.infraScore),
                AVG(v.cleanScore),
                AVG(v.sizeScore),
                AVG(v.accessScore)
            )
            FROM VisitEntity v
            JOIN v.area a
            WHERE a.deletedAt IS NULL
            GROUP BY a.id, a.name
            ORDER BY a.id ASC
            """)
    List<AreaScoreStatistics> summarizeScoresByActiveArea();
}
