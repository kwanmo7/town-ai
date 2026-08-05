package com.townai.area.repository;

import com.townai.area.entity.AreaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Area 영속성 처리, 활성 Area 조회와 위치 조합 중복 검사를 담당한다.
 *
 * <p>메서드명에서 활성 Area는 {@code deletedAt IS NULL}인 Row를 뜻한다.</p>
 */
public interface AreaRepository extends JpaRepository<AreaEntity, Long> {

    /**
     * 모든 활성 Area를 안정적인 순서로 조회한다.
     *
     * @return 논리 삭제되지 않은 모든 Area. ID 오름차순
     */
    List<AreaEntity> findAllByDeletedAtIsNullOrderByIdAsc();

    /**
     * 활성 Area 수를 계산한다.
     *
     * @return 논리 삭제되지 않은 Area 수
     */
    long countByDeletedAtIsNull();

    /**
     * ID와 활성 상태를 함께 확인한다.
     *
     * @param id 조회할 Area ID
     * @return 활성 Area. 없거나 논리 삭제됐으면 빈 Optional
     */
    Optional<AreaEntity> findByIdAndDeletedAtIsNull(Long id);

    /**
     * 정규화된 위치 조합으로 활성 Area를 조회한다.
     *
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param name 동네 이름
     * @return 같은 위치 조합의 활성 Area
     */
    Optional<AreaEntity> findByPrefectureAndCityAndNameAndDeletedAtIsNull(
            String prefecture,
            String city,
            String name
    );

    /**
     * DB UNIQUE 정책에 맞춰 Soft Delete 여부와 관계없이 같은 위치 조합을 검사한다.
     *
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param name 동네 이름
     * @return 동일한 위치 조합의 Row가 하나라도 있으면 {@code true}
     */
    boolean existsByPrefectureAndCityAndName(
            String prefecture,
            String city,
            String name
    );

    /**
     * 수정 대상 자신을 제외하고 동일 위치 조합이 존재하는지 검사한다.
     *
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param name 동네 이름
     * @param id 중복 검사에서 제외할 수정 대상 Area ID
     * @return 다른 Row에 동일 위치 조합이 존재하면 {@code true}
     */
    boolean existsByPrefectureAndCityAndNameAndIdNot(
            String prefecture,
            String city,
            String name,
            Long id
    );

    /**
     * 기준 시각 이후 Area 생성·수정·논리 삭제 여부를 확인한다.
     *
     * @param since Report 생성 시각
     * @return 전체 Report 입력을 무효화할 Area 변경이 있으면 {@code true}
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM AreaEntity a
            WHERE a.updatedAt > :since OR a.deletedAt > :since
            """)
    boolean existsChangedAfter(@Param("since") Instant since);

    /**
     * 선택된 Area 중 기준 시각 이후 변경된 Row가 있는지 확인한다.
     *
     * @param areaIds 선택된 Area ID
     * @param since Report 생성 시각
     * @return AREA·COMPARE 입력을 무효화할 변경이 있으면 {@code true}
     */
    @Query("""
            SELECT CASE WHEN COUNT(a) > 0 THEN true ELSE false END
            FROM AreaEntity a
            WHERE a.id IN :areaIds
              AND (a.updatedAt > :since OR a.deletedAt > :since)
            """)
    boolean existsChangedAfterForAreaIds(
            @Param("areaIds") List<Long> areaIds,
            @Param("since") Instant since
    );
}
