package com.townai.report.generation;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.entity.ReportType;
import com.townai.statistics.model.OverallStatistics;
import com.townai.statistics.service.StatisticsService;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToIntFunction;

/**
 * Report 요청을 검증하고 DB 데이터를 유형별 Prompt 입력 구조로 변환한다.
 *
 * <p>SUMMARY는 Statistics Service의 집계 결과를 재사용하고, AREA·COMPARE·ALL은
 * 선택된 Area와 원본 Visit을 정해진 순서로 조립한다. AI가 통계나 대상 선택을
 * 임의로 재계산하지 않도록 평균과 표시 순서는 Backend에서 확정한다.</p>
 */
@Component
@Transactional(readOnly = true)
public class ReportDataAssembler {

    private final AreaRepository areaRepository;
    private final VisitRepository visitRepository;
    private final StatisticsService statisticsService;

    /**
     * Report Prompt 입력 조립기를 생성한다.
     *
     * @param areaRepository 활성 Area 조회 Repository
     * @param visitRepository Report 대상 Visit 조회 Repository
     * @param statisticsService SUMMARY가 재사용할 통계 Service
     */
    public ReportDataAssembler(
            AreaRepository areaRepository,
            VisitRepository visitRepository,
            StatisticsService statisticsService
    ) {
        this.areaRepository = areaRepository;
        this.visitRepository = visitRepository;
        this.statisticsService = statisticsService;
    }

    /**
     * Report 유형별 대상 규칙을 검증하고 Prompt 입력을 준비한다.
     *
     * <ul>
     *   <li>SUMMARY/ALL: {@code areaIds} 필드 생략</li>
     *   <li>AREA: 중복 없는 활성 Area 1개</li>
     *   <li>COMPARE: 중복 없는 활성 Area 2~5개</li>
     * </ul>
     *
     * @param request Report 생성 요청
     * @return 검증된 유형, 대상 Area와 유형별 Prompt 입력
     * @throws ApiException 유형·대상 조합이 잘못됐거나 Area가 없거나 삭제된 경우
     */
    public ReportGenerationData prepare(ReportCreateRequest request) {
        ReportType type = ReportType.from(request.getReportType());
        validateTargets(type, request);

        return switch (type) {
            case SUMMARY -> prepareSummary();
            case ALL -> prepareAll();
            case AREA -> prepareArea(request.getAreaIds().getFirst());
            case COMPARE -> prepareCompare(request.getAreaIds());
        };
    }

    private ReportGenerationData prepareSummary() {
        OverallStatistics statistics =
                statisticsService.getOverallStatistics();

        SummaryInput input = new SummaryInput(
                Math.toIntExact(statistics.areaCount()),
                Math.toIntExact(statistics.visitCount()),
                scoreAverages(statistics.averageScores()),
                new TopFive(
                        topAreas(statistics.top5().atmosphere()),
                        topAreas(statistics.top5().infra()),
                        topAreas(statistics.top5().clean()),
                        topAreas(statistics.top5().size()),
                        topAreas(statistics.top5().access())
                )
        );
        return new ReportGenerationData(ReportType.SUMMARY, List.of(), input);
    }

    private ReportGenerationData prepareArea(Long areaId) {
        AreaEntity area = findActiveArea(areaId);
        List<VisitEntity> visits = visitRepository.findAllByAreaIdsForReport(List.of(areaId));
        AreaInput input = new AreaInput(
                areaInfo(area),
                new AreaStatistics(visits.size(), averageScores(visits)),
                visitInputs(visits)
        );
        return new ReportGenerationData(ReportType.AREA, List.of(area), input);
    }

    private ReportGenerationData prepareCompare(List<Long> areaIds) {
        List<AreaEntity> areas = findActiveAreasInRequestOrder(areaIds);
        Map<Long, List<VisitEntity>> visitsByArea = groupByArea(
                visitRepository.findAllByAreaIdsForReport(areaIds)
        );
        List<CompareAreaInput> areaInputs = new ArrayList<>();
        for (int index = 0; index < areas.size(); index++) {
            AreaEntity area = areas.get(index);
            List<VisitEntity> visits = visitsByArea.getOrDefault(area.getId(), List.of());
            areaInputs.add(new CompareAreaInput(
                    index + 1,
                    area.getId(),
                    area.getName(),
                    visits.size(),
                    averageScores(visits),
                    visits.stream()
                            .map(VisitEntity::getMemo)
                            .filter(memo -> memo != null && !memo.isBlank())
                            .toList()
            ));
        }
        return new ReportGenerationData(
                ReportType.COMPARE,
                areas,
                new CompareInput(areaInputs)
        );
    }

    private ReportGenerationData prepareAll() {
        List<AreaEntity> areas = areaRepository.findAllByDeletedAtIsNullOrderByIdAsc();
        Map<Long, List<VisitEntity>> visitsByArea = groupByArea(
                visitRepository.findAllForActiveAreas()
        );
        List<AllAreaInput> areaInputs = new ArrayList<>();
        for (int index = 0; index < areas.size(); index++) {
            AreaEntity area = areas.get(index);
            List<VisitEntity> visits = visitsByArea.getOrDefault(area.getId(), List.of());
            areaInputs.add(new AllAreaInput(
                    index + 1,
                    area.getId(),
                    area.getName(),
                    area.getPrefecture(),
                    area.getCity(),
                    area.getStation(),
                    visits.size(),
                    averageScores(visits),
                    visitInputs(visits)
            ));
        }
        return new ReportGenerationData(
                ReportType.ALL,
                areas,
                new AllInput(areaInputs)
        );
    }

    private void validateTargets(ReportType type, ReportCreateRequest request) {
        List<Long> areaIds = request.getAreaIds();
        if (type == ReportType.SUMMARY || type == ReportType.ALL) {
            if (request.isAreaIdsProvided()) {
                throw new ApiException(ErrorCode.INVALID_REPORT_TARGETS);
            }
            return;
        }
        if (!request.isAreaIdsProvided()
                || areaIds == null
                || areaIds.stream().anyMatch(Objects::isNull)) {
            throw new ApiException(ErrorCode.INVALID_REPORT_TARGETS);
        }
        int expectedMinimum = type == ReportType.AREA ? 1 : 2;
        int expectedMaximum = type == ReportType.AREA ? 1 : 5;
        if (areaIds.size() < expectedMinimum
                || areaIds.size() > expectedMaximum
                || areaIds.stream().distinct().count() != areaIds.size()) {
            throw new ApiException(ErrorCode.INVALID_REPORT_TARGETS);
        }
    }

    private List<AreaEntity> findActiveAreasInRequestOrder(List<Long> areaIds) {
        return areaIds.stream().map(this::findActiveArea).toList();
    }

    private AreaEntity findActiveArea(Long areaId) {
        return areaRepository.findByIdAndDeletedAtIsNull(areaId)
                .orElseThrow(() -> new ApiException(ErrorCode.AREA_NOT_FOUND));
    }

    private Map<Long, List<VisitEntity>> groupByArea(List<VisitEntity> visits) {
        Map<Long, List<VisitEntity>> result = new LinkedHashMap<>();
        for (VisitEntity visit : visits) {
            result.computeIfAbsent(visit.getArea().getId(), ignored -> new ArrayList<>())
                    .add(visit);
        }
        return result;
    }

    private List<VisitInput> visitInputs(List<VisitEntity> visits) {
        return visits.stream()
                .map(visit -> new VisitInput(
                        visit.getVisitDate(),
                        visit.getAtmosphereScore(),
                        visit.getInfraScore(),
                        visit.getCleanScore(),
                        visit.getSizeScore(),
                        visit.getAccessScore(),
                        visit.getMemo()
                ))
                .toList();
    }

    private AreaInfo areaInfo(AreaEntity area) {
        return new AreaInfo(
                area.getId(),
                area.getName(),
                area.getPrefecture(),
                area.getCity(),
                area.getStation()
        );
    }

    private ScoreAverages averageScores(List<VisitEntity> visits) {
        if (visits.isEmpty()) {
            return new ScoreAverages(null, null, null, null, null);
        }
        return new ScoreAverages(
                average(visits, VisitEntity::getAtmosphereScore),
                average(visits, VisitEntity::getInfraScore),
                average(visits, VisitEntity::getCleanScore),
                average(visits, VisitEntity::getSizeScore),
                average(visits, VisitEntity::getAccessScore)
        );
    }

    private Double average(List<VisitEntity> visits, ToIntFunction<VisitEntity> getter) {
        double average = visits.stream().mapToInt(getter).average().orElseThrow();
        return round(average);
    }

    private Double round(Double average) {
        if (average == null) {
            return null;
        }
        return BigDecimal.valueOf(average)
                .setScale(1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<TopArea> topAreas(
            List<com.townai.statistics.model.TopArea> areas
    ) {
        return areas.stream()
                .map(area -> new TopArea(
                        area.areaId(),
                        area.areaName(),
                        area.score()
                ))
                .toList();
    }

    private ScoreAverages scoreAverages(
            com.townai.statistics.model.ScoreAverages statistics
    ) {
        return new ScoreAverages(
                statistics.atmosphere(),
                statistics.infra(),
                statistics.clean(),
                statistics.size(),
                statistics.access()
        );
    }

    /**
     * Prompt에 전달할 항목별 평균이다.
     *
     * @param atmosphere 분위기 평균
     * @param infra 생활 인프라 평균
     * @param clean 청결도 평균
     * @param size 넓은 집 가능성 평균
     * @param access 접근성 평균
     */
    public record ScoreAverages(
            Double atmosphere,
            Double infra,
            Double clean,
            Double size,
            Double access
    ) {
    }

    /**
     * Prompt에 전달하는 원본 Visit 평가이다.
     *
     * @param visitDate 방문일
     * @param atmosphereScore 분위기 점수
     * @param infraScore 생활 인프라 점수
     * @param cleanScore 청결도 점수
     * @param sizeScore 넓은 집 가능성 점수
     * @param accessScore 접근성 점수
     * @param memo 방문 메모
     */
    public record VisitInput(
            LocalDate visitDate,
            int atmosphereScore,
            int infraScore,
            int cleanScore,
            int sizeScore,
            int accessScore,
            String memo
    ) {
    }

    /**
     * AREA Report에 전달하는 대상 Area의 위치 정보이다.
     *
     * @param id Area 식별자
     * @param name 동네 이름
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param station 인접 역 이름
     */
    public record AreaInfo(
            Long id,
            String name,
            String prefecture,
            String city,
            String station
    ) {
    }

    /**
     * AREA Report에 전달하는 Visit 집계이다.
     *
     * @param visitCount 대상 Area의 Visit 수
     * @param averageScores 항목별 평균
     */
    public record AreaStatistics(int visitCount, ScoreAverages averageScores) {
    }

    /**
     * SUMMARY Prompt의 항목별 순위 한 건이다.
     *
     * @param areaId Area 식별자
     * @param areaName Area 이름
     * @param score 해당 항목의 Area별 평균
     */
    public record TopArea(Long areaId, String areaName, Double score) {
    }

    /**
     * SUMMARY Prompt에 전달하는 다섯 항목별 Top 5이다.
     *
     * @param atmosphere 분위기 상위 Area
     * @param infra 생활 인프라 상위 Area
     * @param clean 청결도 상위 Area
     * @param size 넓은 집 가능성 상위 Area
     * @param access 접근성 상위 Area
     */
    public record TopFive(
            List<TopArea> atmosphere,
            List<TopArea> infra,
            List<TopArea> clean,
            List<TopArea> size,
            List<TopArea> access
    ) {
    }

    /**
     * SQL 통계를 중심으로 짧은 AI Comment를 생성하는 SUMMARY 입력이다.
     *
     * @param areaCount 활성 Area 수
     * @param visitCount 집계된 Visit 수
     * @param averageScores 모든 Visit의 항목별 평균
     * @param top5 Area별 평균 순위
     */
    public record SummaryInput(
            int areaCount,
            int visitCount,
            ScoreAverages averageScores,
            TopFive top5
    ) {
    }

    /**
     * 한 Area의 상세 분석에 사용하는 AREA 입력이다.
     *
     * @param area 대상 Area 정보
     * @param statistics 대상 Area의 집계
     * @param visits 방문일 순으로 정렬된 원본 평가
     */
    public record AreaInput(
            AreaInfo area,
            AreaStatistics statistics,
            List<VisitInput> visits
    ) {
    }

    /**
     * COMPARE Prompt에 전달하는 비교 대상 한 곳의 입력이다.
     *
     * @param displayOrder 요청에서 선택한 1부터 시작하는 표시 순서
     * @param id Area 식별자
     * @param name Area 이름
     * @param visitCount 대상 Area의 Visit 수
     * @param averageScores 항목별 평균
     * @param memos 비어 있지 않은 원본 방문 메모
     */
    public record CompareAreaInput(
            int displayOrder,
            Long id,
            String name,
            int visitCount,
            ScoreAverages averageScores,
            List<String> memos
    ) {
    }

    /**
     * COMPARE Report의 전체 Prompt 입력이다.
     *
     * @param areas 요청 순서를 보존한 2~5개의 비교 대상
     */
    public record CompareInput(List<CompareAreaInput> areas) {
    }

    /**
     * ALL Prompt에 전달하는 활성 Area 한 곳의 상세 입력이다.
     *
     * @param displayOrder 전체 목록의 1부터 시작하는 표시 순서
     * @param id Area 식별자
     * @param name Area 이름
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param station 인접 역 이름
     * @param visitCount 대상 Area의 Visit 수
     * @param averageScores 항목별 평균
     * @param visits 방문일 순으로 정렬된 원본 평가
     */
    public record AllAreaInput(
            int displayOrder,
            Long id,
            String name,
            String prefecture,
            String city,
            String station,
            int visitCount,
            ScoreAverages averageScores,
            List<VisitInput> visits
    ) {
    }

    /**
     * ALL Report의 전체 Prompt 입력이다.
     *
     * @param areas ID 오름차순으로 정렬된 모든 활성 Area 입력
     */
    public record AllInput(List<AllAreaInput> areas) {
    }
}
