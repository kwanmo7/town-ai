package com.townai.statistics.service;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.statistics.model.AreaStatistics;
import com.townai.statistics.model.OverallStatistics;
import com.townai.statistics.repository.StatisticsRepository;
import com.townai.statistics.repository.projection.AreaScoreStatistics;
import com.townai.statistics.repository.projection.ScoreStatistics;
import com.townai.statistics.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    @Mock
    private StatisticsRepository statisticsRepository;

    private StatisticsService statisticsService;

    @BeforeEach
    void setUp() {
        statisticsService = new StatisticsServiceImpl(
                areaRepository,
                statisticsRepository
        );
    }

    @Test
    void returnsRoundedOverallAveragesAndSortedTopFive() {
        when(areaRepository.countByDeletedAtIsNull()).thenReturn(6L);
        when(statisticsRepository.summarizeActiveAreaVisits()).thenReturn(
                new ScoreStatistics(12L, 8.45, 8.14, 8.85, 8.25, 7.24)
        );
        when(statisticsRepository.summarizeScoresByActiveArea()).thenReturn(List.of(
                areaScores(1L, "A", 8.44),
                areaScores(2L, "B", 9.04),
                areaScores(3L, "C", 9.04),
                areaScores(4L, "D", 7.04),
                areaScores(5L, "E", 6.04),
                areaScores(6L, "F", 5.04)
        ));

        OverallStatistics result =
                statisticsService.getOverallStatistics();

        assertEquals(6, result.areaCount());
        assertEquals(12, result.visitCount());
        assertEquals(8.5, result.averageScores().atmosphere());
        assertEquals(8.1, result.averageScores().infra());
        assertEquals(
                List.of(2L, 3L, 1L, 4L, 5L),
                result.top5().atmosphere().stream()
                        .map(item -> item.areaId())
                        .toList()
        );
        assertEquals(9.0, result.top5().atmosphere().getFirst().score());
    }

    @Test
    void returnsAreaStatisticsForActiveArea() {
        AreaEntity area = area(1L, "센터미나미");
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));
        when(statisticsRepository.summarizeActiveAreaVisitsByAreaId(1L))
                .thenReturn(new ScoreStatistics(
                        3L,
                        8.65,
                        9.04,
                        8.34,
                        8.04,
                        7.65
                ));

        AreaStatistics result = statisticsService.getAreaStatistics(1L);

        assertEquals(1L, result.areaId());
        assertEquals("센터미나미", result.areaName());
        assertEquals(3, result.visitCount());
        assertEquals(8.7, result.averageScores().atmosphere());
        assertEquals(7.7, result.averageScores().access());
    }

    @Test
    void rejectsMissingOrDeletedArea() {
        when(areaRepository.findByIdAndDeletedAtIsNull(99L))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> statisticsService.getAreaStatistics(99L)
        );

        assertEquals(ErrorCode.AREA_NOT_FOUND, exception.errorCode());
    }

    private AreaScoreStatistics areaScores(Long id, String name, double score) {
        return new AreaScoreStatistics(
                id,
                name,
                score,
                score,
                score,
                score,
                score
        );
    }

    private AreaEntity area(Long id, String name) {
        AreaEntity area = AreaEntity.builder()
                .name(name)
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
        ReflectionTestUtils.setField(area, "id", id);
        return area;
    }
}
