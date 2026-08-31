package com.townai.visit.repository;

import com.townai.visit.entity.VisitEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/** Visit 문서의 저장·조회·삭제와 Report 변경 감지를 추상화하는 영속성 경계이다. */
public interface VisitRepository {

    /**
     * 신규 Visit을 저장하거나 기존 Visit 전체 값을 갱신한다.
     *
     * @param visit 저장할 Visit
     * @return ID와 저장 시각이 반영된 Visit
     */
    VisitEntity save(VisitEntity visit);

    /**
     * Visit을 ID로 조회한다.
     *
     * @param id Visit ID
     * @return 존재하는 Visit
     */
    Optional<VisitEntity> findById(Long id);

    /**
     * 선택 조건을 모두 적용해 활성 Area의 Visit 목록을 조회한다.
     *
     * @param areaId 선택적 Area ID
     * @param fromDate 선택적 시작일
     * @param toDate 선택적 종료일
     * @return 조건에 맞는 Visit 목록
     */
    List<VisitEntity> findAllByFilters(
            Long areaId,
            LocalDate fromDate,
            LocalDate toDate
    );

    /**
     * 통계 계산에 사용할 활성 Area의 전체 Visit을 조회한다.
     *
     * @return 활성 Area의 Visit 목록
     */
    List<VisitEntity> findAllForActiveAreas();

    /**
     * 지정한 Area의 Report 분석에 사용할 Visit을 조회한다.
     *
     * @param areaIds 분석 대상 Area ID 목록
     * @return 대상 Area의 Visit 목록
     */
    List<VisitEntity> findAllByAreaIdsForReport(List<Long> areaIds);

    /**
     * 기준 시각 이후 수정된 Visit이 있는지 확인한다.
     *
     * @param since 비교 기준 시각
     * @return 수정된 Visit이 있으면 {@code true}
     */
    boolean existsByUpdatedAtAfter(Instant since);

    /**
     * 지정한 Area의 Visit 중 기준 시각 이후 수정된 항목이 있는지 확인한다.
     *
     * @param areaIds 확인할 Area ID 목록
     * @param since 비교 기준 시각
     * @return 수정된 Visit이 있으면 {@code true}
     */
    boolean existsByArea_IdInAndUpdatedAtAfter(
            List<Long> areaIds,
            Instant since
    );

    /**
     * Visit 문서를 물리 삭제한다.
     *
     * @param visit 삭제할 Visit
     */
    void delete(VisitEntity visit);
}
