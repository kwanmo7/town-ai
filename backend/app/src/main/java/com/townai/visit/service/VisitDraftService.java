package com.townai.visit.service;

import com.townai.common.error.ApiException;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;

/**
 * 자연어 방문 평가를 저장되지 않은 Visit 초안으로 변환하는 계약이다.
 *
 * <p>반환된 초안은 사용자가 검토할 자료일 뿐이며 Visit 생성 Transaction을 시작하지 않는다.</p>
 */
public interface VisitDraftService {

    /**
     * 자연어를 활성 Area 목록과 현재 생활권 날짜를 기준으로 파싱한다.
     *
     * @param request 자연어 방문 평가
     * @return 확정 가능한 값과 사용자 확인 경고를 포함한 초안
     * @throws ApiException AI 호출 또는 교정 출력 검증에 실패한 경우
     */
    VisitDraftResponse create(VisitDraftRequest request);
}
