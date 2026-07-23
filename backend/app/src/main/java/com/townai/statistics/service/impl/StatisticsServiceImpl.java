package com.townai.statistics.service.impl;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.statistics.model.AreaStatistics;
import com.townai.statistics.model.OverallStatistics;
import com.townai.statistics.model.ScoreAverages;
import com.townai.statistics.model.TopArea;
import com.townai.statistics.model.TopFive;
import com.townai.statistics.repository.StatisticsRepository;
import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;
import com.townai.statistics.service.StatisticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/**
 * DB 집계 Projection을 Statistics 내부 모델로 조립한다.
 *
 * <p>평균은 모든 Visit을 같은 비중으로 계산하고 소수점 첫째 자리에서
 * {@link RoundingMode#HALF_UP}으로 반올림한다. Top 5는 Area별 평균 내림차순이며,
 * 동점일 때 Area ID 오름차순으로 결과를 고정한다. 논리 삭제된 Area는 모든 집계에서
 * 제외한다.</p>
 */
@Service
@Transactional(readOnly = true)
public class StatisticsServiceImpl implements StatisticsService {

    private final AreaRepository areaRepository;
    private final StatisticsRepository statisticsRepository;

    /**
     * Statistics Service를 생성한다.
     *
     * @param areaRepository 활성 Area 확인과 개수 조회 Repository
     * @param statisticsRepository Visit 평균 집계 전용 Repository
     */
    public StatisticsServiceImpl(
            AreaRepository areaRepository,
            StatisticsRepository statisticsRepository
    ) {
        this.areaRepository = areaRepository;
        this.statisticsRepository = statisticsRepository;
    }

    @Override
    public OverallStatistics getOverallStatistics() {
        ScoreStatistics total =
                statisticsRepository.summarizeActiveAreaVisits();
        List<AreaScoreStatistics> areaScores =
                statisticsRepository.summarizeScoresByActiveArea();

        return new OverallStatistics(
                areaRepository.countByDeletedAtIsNull(),
                total.visitCount(),
                scoreAverages(total),
                new TopFive(
                        topFive(areaScores, AreaScoreStatistics::atmosphere),
                        topFive(areaScores, AreaScoreStatistics::infra),
                        topFive(areaScores, AreaScoreStatistics::clean),
                        topFive(areaScores, AreaScoreStatistics::size),
                        topFive(areaScores, AreaScoreStatistics::access)
                )
        );
    }

    @Override
    public AreaStatistics getAreaStatistics(Long areaId) {
        AreaEntity area = areaRepository.findByIdAndDeletedAtIsNull(areaId)
                .orElseThrow(() -> new ApiException(ErrorCode.AREA_NOT_FOUND));
        ScoreStatistics statistics =
                statisticsRepository.summarizeActiveAreaVisitsByAreaId(areaId);

        return new AreaStatistics(
                area.getId(),
                area.getName(),
                statistics.visitCount(),
                scoreAverages(statistics)
        );
    }

    private ScoreAverages scoreAverages(ScoreStatistics statistics) {
        return new ScoreAverages(
                round(statistics.atmosphere()),
                round(statistics.infra()),
                round(statistics.clean()),
                round(statistics.size()),
                round(statistics.access())
        );
    }

    private List<TopArea> topFive(
            List<AreaScoreStatistics> areaScores,
            Function<AreaScoreStatistics, Double> scoreGetter
    ) {
        return areaScores.stream()
                .map(area -> new TopArea(
                        area.areaId(),
                        area.areaName(),
                        round(scoreGetter.apply(area))
                ))
                .sorted(Comparator.comparing(TopArea::score).reversed()
                        .thenComparing(TopArea::areaId))
                .limit(5)
                .toList();
    }

    private Double round(Double average) {
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(average)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
