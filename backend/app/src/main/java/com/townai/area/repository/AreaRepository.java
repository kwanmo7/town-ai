package com.townai.area.repository;

import com.townai.area.entity.AreaEntity;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Area 문서의 저장·조회와 변경 여부 확인을 추상화하는 영속성 경계이다. */
public interface AreaRepository {

    /**
     * 신규 Area를 저장하거나 기존 Area 전체 값을 갱신한다.
     *
     * @param area 저장할 Area
     * @return ID와 저장 시각이 반영된 Area
     */
    AreaEntity save(AreaEntity area);

    /**
     * 논리 삭제되지 않은 Area를 ID 오름차순으로 조회한다.
     *
     * @return 활성 Area 목록
     */
    List<AreaEntity> findAllByDeletedAtIsNullOrderByIdAsc();

    /**
     * 활성 Area 수를 반환한다.
     *
     * @return 논리 삭제되지 않은 Area 수
     */
    long countByDeletedAtIsNull();

    /**
     * 활성 Area를 ID로 조회한다.
     *
     * @param id Area ID
     * @return 논리 삭제되지 않은 Area
     */
    Optional<AreaEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 삭제 상태와 관계없이 ID로 Area를 조회한다.
     *
     * @param id Area ID
     * @return 존재하는 Area
     */
    Optional<AreaEntity> findById(Long id);

    /**
     * 활성 Area를 행정구역과 이름으로 조회한다.
     *
     * @param prefecture 도도부현
     * @param city 시·구
     * @param name 지역 이름
     * @return 조건에 맞는 활성 Area
     */
    Optional<AreaEntity> findByPrefectureAndCityAndNameAndDeletedAtIsNull(
            String prefecture,
            String city,
            String name
    );

    /**
     * 동일한 행정구역과 이름으로 예약된 Area Key가 있는지 확인한다.
     *
     * @param prefecture 도도부현
     * @param city 시·구
     * @param name 지역 이름
     * @return 예약된 Key가 있으면 {@code true}
     */
    boolean existsByPrefectureAndCityAndName(
            String prefecture,
            String city,
            String name
    );

    /**
     * 수정 대상 외의 Area가 동일한 복합 고유 키를 사용하는지 확인한다.
     *
     * @param prefecture 도도부현
     * @param city 시·구
     * @param name 지역 이름
     * @param id 제외할 Area ID
     * @return 다른 Area가 Key를 사용하면 {@code true}
     */
    boolean existsByPrefectureAndCityAndNameAndIdNot(
            String prefecture,
            String city,
            String name,
            Long id
    );

    /**
     * 특정 시각 이후 Area 생성·수정·삭제가 있었는지 확인한다.
     *
     * @param since 비교 기준 시각
     * @return 기준 시각 이후 변경이 있으면 {@code true}
     */
    boolean existsChangedAfter(Instant since);

    /**
     * 지정한 Area 중 기준 시각 이후 변경된 항목이 있는지 확인한다.
     *
     * @param areaIds 확인할 Area ID 목록
     * @param since 비교 기준 시각
     * @return 변경된 Area가 있으면 {@code true}
     */
    boolean existsChangedAfterForAreaIds(List<Long> areaIds, Instant since);
}
