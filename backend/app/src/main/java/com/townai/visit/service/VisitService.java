package com.townai.visit.service;

import com.townai.common.error.ApiException;
import com.townai.visit.dto.VisitDetailResponse;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.dto.VisitRequest;
import com.townai.visit.dto.VisitSummaryResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Visit 생성·조회·전체 수정·물리 삭제의 비즈니스 계약을 정의한다.
 */
public interface VisitService {

    /**
     * 활성 Area에 새 Visit을 생성한다.
     *
     * @param request 생성할 Visit 전체 값
     * @return 생성된 Visit
     * @throws ApiException Area가 없거나 논리 삭제된 경우
     */
    VisitMutationResponse create(VisitRequest request);

    /**
     * 선택 조건에 맞는 Visit을 조회한다.
     *
     * @param areaId 선택적인 Area ID
     * @param from 포함되는 방문일 하한
     * @param to 포함되는 방문일 상한
     * @return 최근 방문 순의 Visit 요약 목록
     * @throws ApiException {@code from}이 {@code to}보다 늦은 경우
     */
    List<VisitSummaryResponse> findAll(Long areaId, LocalDate from, LocalDate to);

    /**
     * Visit 한 건을 상세 조회한다.
     *
     * @param visitId 조회할 Visit ID
     * @return Area 정보를 포함한 Visit 상세
     * @throws ApiException Visit이 존재하지 않는 경우
     */
    VisitDetailResponse findById(Long visitId);

    /**
     * Visit의 모든 입력 값을 교체한다.
     *
     * @param visitId 수정할 Visit ID
     * @param request 교체할 전체 값
     * @return 수정된 Visit
     * @throws ApiException Visit 또는 대상 Area가 없거나 Area가 삭제된 경우
     */
    VisitMutationResponse update(Long visitId, VisitRequest request);

    /**
     * Visit을 물리 삭제한다.
     *
     * @param visitId 삭제할 Visit ID
     * @throws ApiException Visit이 존재하지 않는 경우
     */
    void delete(Long visitId);
}
