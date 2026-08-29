package com.townai.statistics.repository;

import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;

import java.util.List;

/** Aggregation boundary for Statistics calculated from active Area Visits. */
public interface StatisticsRepository {

    ScoreStatistics summarizeActiveAreaVisits();

    ScoreStatistics summarizeActiveAreaVisitsByAreaId(Long areaId);

    List<AreaScoreStatistics> summarizeScoresByActiveArea();
}
