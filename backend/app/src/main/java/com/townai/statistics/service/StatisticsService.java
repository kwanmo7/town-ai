package com.townai.statistics.service;

import com.townai.common.error.ApiException;
import com.townai.statistics.model.AreaStatistics;
import com.townai.statistics.model.OverallStatistics;

/**
 * 전체 및 Area별 Visit 통계를 API 표현과 무관한 내부 모델로 제공한다.
 *
 * <p>동일한 계산 결과를 Statistics API와 SUMMARY Report가 공유한다.</p>
 */
public interface StatisticsService {

    /**
     * 활성 Area에 속한 모든 Visit을 같은 비중으로 집계한다.
     *
     * @return 전체 평균과 항목별 Area Top 5
     */
    OverallStatistics getOverallStatistics();

    /**
     * 활성 Area 한 곳에 속한 Visit을 집계한다.
     *
     * @param areaId 조회할 Area ID
     * @return Area 이름, Visit 수와 평균 점수
     * @throws ApiException Area가 없거나 논리 삭제된 경우
     */
    AreaStatistics getAreaStatistics(Long areaId);
}
