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

/** 개인용 소규모 Visit 문서를 메모리에서 집계하는 Statistics 구현이다. */
@Repository
public class FirestoreStatisticsRepository implements StatisticsRepository {

    private final VisitRepository visitRepository;

    /**
     * Visit 조회 결과를 사용하는 통계 Repository를 생성한다.
     *
     * @param visitRepository 활성 Area Visit 조회 경계
     */
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
        // 수십 건 규모의 V1에서는 추가 집계 문서를 운영하는 것보다 한 번 읽어 계산하는 편이 단순하다.
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
