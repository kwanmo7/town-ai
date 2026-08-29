package com.townai.visit.repository;

import com.townai.visit.entity.VisitEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for Visit documents. */
public interface VisitRepository {

    VisitEntity save(VisitEntity visit);

    Optional<VisitEntity> findById(Long id);

    List<VisitEntity> findAllByFilters(
            Long areaId,
            LocalDate fromDate,
            LocalDate toDate
    );

    List<VisitEntity> findAllForActiveAreas();

    List<VisitEntity> findAllByAreaIdsForReport(List<Long> areaIds);

    boolean existsByUpdatedAtAfter(Instant since);

    boolean existsByArea_IdInAndUpdatedAtAfter(
            List<Long> areaIds,
            Instant since
    );

    void delete(VisitEntity visit);
}
