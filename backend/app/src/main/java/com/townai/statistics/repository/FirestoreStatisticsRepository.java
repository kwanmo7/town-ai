package com.townai.statistics.repository;

import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Calculates the small personal dataset in memory from Firestore documents. */
@Repository
public class FirestoreStatisticsRepository implements StatisticsRepository {

    private final VisitRepository visitRepository;

    public FirestoreStatisticsRepository(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @Override
    public ScoreStatistics summarizeActiveAreaVisits() {
        return summarize(visitRepository.findAllForActiveAreas());
    }

    @Override
    public ScoreStatistics summarizeActiveAreaVisitsByAreaId(Long areaId) {
        return summarize(visitRepository.findAllForActiveAreas().stream()
                .filter(visit -> visit.getArea().getId().equals(areaId))
                .toList());
    }

    @Override
    public List<AreaScoreStatistics> summarizeScoresByActiveArea() {
        Map<Long, List<VisitEntity>> byArea = new LinkedHashMap<>();
        for (VisitEntity visit : visitRepository.findAllForActiveAreas()) {
            byArea.computeIfAbsent(
                    visit.getArea().getId(),
                    ignored -> new ArrayList<>()
            ).add(visit);
        }

        return byArea.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> {
                    List<VisitEntity> visits = entry.getValue();
                    ScoreStatistics scores = summarize(visits);
                    return new AreaScoreStatistics(
                            entry.getKey(),
                            visits.getFirst().getArea().getName(),
                            scores.atmosphere(),
                            scores.infra(),
                            scores.clean(),
                            scores.size(),
                            scores.access()
                    );
                })
                .sorted(Comparator.comparing(AreaScoreStatistics::areaId))
                .toList();
    }

    private ScoreStatistics summarize(List<VisitEntity> visits) {
        if (visits.isEmpty()) {
            return new ScoreStatistics(0L, null, null, null, null, null);
        }
        return new ScoreStatistics(
                (long) visits.size(),
                visits.stream().mapToInt(VisitEntity::getAtmosphereScore).average().orElseThrow(),
                visits.stream().mapToInt(VisitEntity::getInfraScore).average().orElseThrow(),
                visits.stream().mapToInt(VisitEntity::getCleanScore).average().orElseThrow(),
                visits.stream().mapToInt(VisitEntity::getSizeScore).average().orElseThrow(),
                visits.stream().mapToInt(VisitEntity::getAccessScore).average().orElseThrow()
        );
    }
}
