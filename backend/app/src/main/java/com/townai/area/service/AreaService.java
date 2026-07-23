package com.townai.area.service;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.dto.AreaRequest;
import com.townai.area.dto.AreaSummaryResponse;
import com.townai.common.error.ApiException;

import java.util.List;

/**
 * Area의 정규화, 중복 검사, 활성 상태 조회와 논리 삭제 계약을 정의한다.
 */
public interface AreaService {

    /**
     * 문자열을 정규화하고 위치 조합이 중복되지 않는 새 Area를 생성한다.
     *
     * @param request 생성할 Area 전체 값
     * @return 생성된 Area 상세 정보
     * @throws ApiException 동일 위치 조합이 이미 존재하는 경우
     */
    AreaDetailResponse create(AreaRequest request);

    /**
     * 모든 활성 Area를 조회한다.
     *
     * @return 활성 Area의 ID 오름차순 목록
     */
    List<AreaSummaryResponse> findAll();

    /**
     * 활성 Area 한 건을 조회한다.
     *
     * @param areaId 조회할 Area ID
     * @return 활성 Area 상세 정보
     * @throws ApiException Area가 없거나 논리 삭제된 경우
     */
    AreaDetailResponse findById(Long areaId);

    /**
     * 활성 Area의 모든 입력 필드를 교체한다.
     *
     * @param areaId 수정할 Area ID
     * @param request 교체할 전체 값
     * @return 수정된 Area 상세 정보
     * @throws ApiException Area가 없거나 삭제됐거나 위치 조합이 중복되는 경우
     */
    AreaDetailResponse update(Long areaId, AreaRequest request);

    /**
     * 활성 Area에 삭제 시각을 기록해 이후 조회 대상에서 제외한다.
     *
     * @param areaId 논리 삭제할 Area ID
     * @throws ApiException Area가 없거나 이미 논리 삭제된 경우
     */
    void delete(Long areaId);
}
