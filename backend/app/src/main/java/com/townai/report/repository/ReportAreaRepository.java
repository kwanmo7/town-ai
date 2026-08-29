package com.townai.report.repository;

import com.townai.report.entity.ReportAreaEntity;

import java.util.List;

/** Stores and resolves the ordered Area IDs embedded in a Report document. */
public interface ReportAreaRepository {

    List<ReportAreaEntity> saveAll(List<ReportAreaEntity> reportAreas);

    List<ReportAreaEntity> findAllWithAreaByReportId(Long reportId);

    void deleteAllByReportId(Long reportId);
}
