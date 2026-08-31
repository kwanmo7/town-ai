package com.townai.report.repository;

import com.townai.report.entity.ReportAreaEntity;

import java.util.List;

/** Report 문서 안에 포함된 분석 대상 Area ID와 표시 순서를 저장·복원한다. */
public interface ReportAreaRepository {

    /**
     * Report가 분석한 Area 목록을 표시 순서대로 저장한다.
     *
     * @param reportAreas 저장할 Report·Area 관계
     * @return 저장된 관계 목록
     */
    List<ReportAreaEntity> saveAll(List<ReportAreaEntity> reportAreas);

    /**
     * 저장된 Area ID를 Report·Area 객체와 다시 연결한다.
     *
     * @param reportId 조회할 Report ID
     * @return 표시 순서가 보존된 관계 목록
     */
    List<ReportAreaEntity> findAllWithAreaByReportId(Long reportId);

    /**
     * Report 문서에서 분석 대상 Area 목록만 비운다.
     *
     * @param reportId 정리할 Report ID
     */
    void deleteAllByReportId(Long reportId);
}
