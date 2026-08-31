package com.townai.statistics.repository;

import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;

import java.util.List;

/** 활성 Area의 Visit을 기준으로 점수 통계를 계산하는 집계 경계이다. */
public interface StatisticsRepository {

    /**
     * 전체 활성 Area의 방문 수와 항목별 평균을 계산한다.
     *
     * @return 전체 점수 통계
     */
    ScoreStatistics summarizeActiveAreaVisits();

    /**
     * Area 한 곳의 방문 수와 항목별 평균을 계산한다.
     *
     * @param areaId 집계할 Area ID
     * @return Area 점수 통계
     */
    ScoreStatistics summarizeActiveAreaVisitsByAreaId(Long areaId);

    /**
     * Area별 항목 평균을 Top 5 계산에 사용할 형태로 반환한다.
     *
     * @return Area별 점수 통계
     */
    List<AreaScoreStatistics> summarizeScoresByActiveArea();
}
