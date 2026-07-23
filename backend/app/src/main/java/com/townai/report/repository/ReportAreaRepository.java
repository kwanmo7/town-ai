package com.townai.report.repository;

import com.townai.report.entity.ReportAreaEntity;
import com.townai.report.entity.ReportAreaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Report와 생성 대상 Area의 연결 Row를 저장하고 표시 순서로 조회한다.
 */
public interface ReportAreaRepository extends JpaRepository<ReportAreaEntity, ReportAreaId> {

    /**
     * 상세 응답에서 Area ID를 사용할 수 있도록 Area를 함께 조회한다.
     *
     * @param reportId 조회할 Report ID
     * @return 생성 당시 표시 순서로 정렬된 Report-Area 연결
     */
    @Query("""
            SELECT ra
            FROM ReportAreaEntity ra
            JOIN FETCH ra.area
            WHERE ra.report.id = :reportId
            ORDER BY ra.displayOrder ASC
            """)
    List<ReportAreaEntity> findAllWithAreaByReportId(@Param("reportId") Long reportId);

    /**
     * Report 메타데이터 삭제 전에 해당 Report의 연결 Row를 일괄 삭제한다.
     *
     * @param reportId 연결을 제거할 Report ID
     */
    @Modifying
    @Query("DELETE FROM ReportAreaEntity ra WHERE ra.report.id = :reportId")
    void deleteAllByReportId(@Param("reportId") Long reportId);
}
