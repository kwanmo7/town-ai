package com.townai.area.repository;

import com.townai.area.entity.AreaEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Persistence boundary for Area documents. */
public interface AreaRepository {

    AreaEntity save(AreaEntity area);

    List<AreaEntity> findAllByDeletedAtIsNullOrderByIdAsc();

    long countByDeletedAtIsNull();

    Optional<AreaEntity> findByIdAndDeletedAtIsNull(Long id);

    Optional<AreaEntity> findById(Long id);

    Optional<AreaEntity> findByPrefectureAndCityAndNameAndDeletedAtIsNull(
            String prefecture,
            String city,
            String name
    );

    boolean existsByPrefectureAndCityAndName(
            String prefecture,
            String city,
            String name
    );

    boolean existsByPrefectureAndCityAndNameAndIdNot(
            String prefecture,
            String city,
            String name,
            Long id
    );

    boolean existsChangedAfter(Instant since);

    boolean existsChangedAfterForAreaIds(List<Long> areaIds, Instant since);
}
