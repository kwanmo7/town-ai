package com.townai.report.repository;

import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Report 메타데이터 영속성과 안정적인 목록 정렬 조회를 담당한다.
 */
public interface ReportRepository extends JpaRepository<ReportEntity, Long> {

    /**
     * 모든 Report를 최근 생성 순으로 조회한다.
     *
     * @return 모든 Report. 생성 시각 내림차순, 동률이면 ID 내림차순
     */
    List<ReportEntity> findAllByOrderByCreatedAtDescIdDesc();

    /**
     * 유형별 Report를 최근 생성 순으로 조회한다.
     *
     * @param reportType 필터링할 Report 유형
     * @return 해당 유형의 Report. 생성 시각 내림차순, 동률이면 ID 내림차순
     */
    List<ReportEntity> findAllByReportTypeOrderByCreatedAtDescIdDesc(ReportType reportType);
}
