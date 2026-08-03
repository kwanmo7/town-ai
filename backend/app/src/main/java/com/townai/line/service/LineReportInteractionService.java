package com.townai.line.service;

import com.townai.area.dto.AreaSummaryResponse;
import com.townai.area.service.AreaService;
import com.townai.line.messaging.LinePushRequest;
import com.townai.line.messaging.LineReportMessageFactory;
import com.townai.line.model.LineCompareToggleCommand;
import com.townai.line.model.LineReportAreaOption;
import com.townai.line.model.LineReportGenerateCommand;
import com.townai.report.dto.ReportCreateRequest;
import com.townai.report.dto.ReportResponse;
import com.townai.report.entity.ReportType;
import com.townai.report.service.ReportService;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * LINE Report 메뉴의 대상 조회·선택 상태와 멱등 생성을 조율한다.
 */
@Service
public class LineReportInteractionService {

    private static final int MAX_COMPARE_AREAS = 5;

    private final AreaService areaService;
    private final VisitRepository visitRepository;
    private final ReportService reportService;
    private final LineReportMessageFactory messageFactory;

    /**
     * LINE Report 상호작용 Service를 생성한다.
     *
     * @param areaService 활성 Area 조회 Service
     * @param visitRepository Report 대상 Visit 조회 Repository
     * @param reportService AI Report 생성 Service
     * @param messageFactory Report Flex Message Factory
     */
    public LineReportInteractionService(
            AreaService areaService,
            VisitRepository visitRepository,
            ReportService reportService,
            LineReportMessageFactory messageFactory
    ) {
        this.areaService = areaService;
        this.visitRepository = visitRepository;
        this.reportService = reportService;
        this.messageFactory = messageFactory;
    }

    /**
     * AREA 또는 COMPARE Report의 최초 대상 선택 화면을 생성한다.
     *
     * @param lineUserId 수신 사용자
     * @param reportType 대상 선택이 필요한 Report 유형
     * @return 동적 Area 선택 메시지
     */
    public LinePushRequest createSelection(
            String lineUserId,
            ReportType reportType
    ) {
        List<LineReportAreaOption> options = reportAreaOptions();
        return switch (reportType) {
            case AREA -> messageFactory.createAreaSelection(
                    lineUserId,
                    options
            );
            case COMPARE -> messageFactory.createCompareSelection(
                    lineUserId,
                    options,
                    List.of(),
                    null
            );
            case SUMMARY, ALL -> throw new IllegalArgumentException(
                    "Report type does not require target selection."
            );
        };
    }

    /**
     * COMPARE 대상 하나를 추가 또는 제거하고 새 선택 화면을 생성한다.
     *
     * @param lineUserId 수신 사용자
     * @param command 선택 상태 전환 명령
     * @return 갱신된 비교 대상 화면
     */
    public LinePushRequest toggleCompareSelection(
            String lineUserId,
            LineCompareToggleCommand command
    ) {
        List<LineReportAreaOption> options = reportAreaOptions();
        Map<Long, LineReportAreaOption> optionById = options.stream()
                .collect(Collectors.toMap(
                        LineReportAreaOption::areaId,
                        Function.identity()
                ));
        LinkedHashSet<Long> selected = command.selectedAreaIds().stream()
                .filter(optionById::containsKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        String warning = null;
        if (!optionById.containsKey(command.areaId())) {
            warning = "선택한 지역은 더 이상 비교할 수 없습니다.";
        } else if (!selected.remove(command.areaId())) {
            if (selected.size() >= MAX_COMPARE_AREAS) {
                warning = "비교 대상은 최대 5개까지 선택할 수 있습니다.";
            } else {
                selected.add(command.areaId());
            }
        }
        List<Long> orderedSelection = options.stream()
                .map(LineReportAreaOption::areaId)
                .filter(selected::contains)
                .toList();
        return messageFactory.createCompareSelection(
                lineUserId,
                options,
                orderedSelection,
                warning
        );
    }

    /**
     * 같은 Webhook Event의 재처리에는 기존 Report를 재사용해 결과 화면을 만든다.
     *
     * @param lineUserId 수신 사용자
     * @param webhookEventId 멱등성 기준 Event ID
     * @param command Report 생성 명령
     * @return 생성 완료 메시지
     */
    public LinePushRequest generate(
            String lineUserId,
            String webhookEventId,
            LineReportGenerateCommand command
    ) {
        ReportCreateRequest request = new ReportCreateRequest();
        request.setReportType(command.reportType().name());
        if (command.reportType() == ReportType.AREA
                || command.reportType() == ReportType.COMPARE) {
            request.setAreaIds(command.areaIds());
        }
        ReportResponse report = reportService.createForLine(
                request,
                webhookEventId
        );
        return messageFactory.createResult(
                lineUserId,
                report,
                targetLabel(command)
        );
    }

    /**
     * mutable한 Area·Visit 사전 검증보다 먼저 기존 Report 결과를 복원한다.
     *
     * @param lineUserId 수신 사용자
     * @param webhookEventId Report 생성 원본 이벤트 ID
     * @param command 원래 Report 생성 명령
     * @return 기존 Report가 있으면 동일한 완료 메시지
     */
    public Optional<LinePushRequest> findExistingResult(
            String lineUserId,
            String webhookEventId,
            LineReportGenerateCommand command
    ) {
        return reportService.findBySourceWebhookEventId(webhookEventId)
                .map(report -> messageFactory.createResult(
                        lineUserId,
                        report,
                        targetLabel(command)
                ));
    }

    /**
     * Report 생성 시작 안내를 만든다.
     *
     * @param lineUserId 수신 사용자
     * @param reportType 생성 유형
     * @return 생성 중 메시지
     */
    public LinePushRequest createGenerating(
            String lineUserId,
            ReportType reportType
    ) {
        return messageFactory.createGenerating(lineUserId, reportType);
    }

    /**
     * Report 생성 안내와 AI 호출 전에 현재 분석 가능한 Visit을 확인한다.
     *
     * <p>LINE의 오래된 선택 메시지를 다시 누른 경우도 있으므로 전체 유형뿐 아니라
     * AREA·COMPARE 선택 ID도 현재 활성 Area와 Visit 기준으로 재검증한다. 최종적인
     * 생성 규칙은 Report Service에서 다시 검증한다.</p>
     *
     * @param lineUserId 수신 사용자
     * @param command Report 생성 명령
     * @return 생성 불가 안내. 생성 가능하면 빈 Optional
     */
    public Optional<LinePushRequest> findUnavailableMessage(
            String lineUserId,
            LineReportGenerateCommand command
    ) {
        List<LineReportAreaOption> options = reportAreaOptions();
        if (command.reportType() == ReportType.SUMMARY
                || command.reportType() == ReportType.ALL) {
            return options.isEmpty()
                    ? Optional.of(messageFactory.createUnavailable(
                            lineUserId,
                            "아직 등록된 방문 기록이 없습니다."
                    ))
                    : Optional.empty();
        }

        Set<Long> availableAreaIds = options.stream()
                .map(LineReportAreaOption::areaId)
                .collect(Collectors.toSet());
        if (!availableAreaIds.containsAll(command.areaIds())) {
            return Optional.of(messageFactory.createUnavailable(
                    lineUserId,
                    "선택한 지역에 분석 가능한 방문 기록이 없습니다."
            ));
        }
        return Optional.empty();
    }

    private List<LineReportAreaOption> reportAreaOptions() {
        List<AreaSummaryResponse> areas = areaService.findAll();
        Map<Long, List<VisitEntity>> visitsByArea = visitRepository
                .findAllForActiveAreas()
                .stream()
                .collect(Collectors.groupingBy(
                        visit -> visit.getArea().getId()
                ));
        List<LineReportAreaOption> options = new ArrayList<>();
        for (AreaSummaryResponse area : areas) {
            List<VisitEntity> visits = visitsByArea.getOrDefault(
                    area.id(),
                    List.of()
            );
            if (visits.isEmpty()) {
                continue;
            }
            LocalDate latestVisitDate = visits.stream()
                    .map(VisitEntity::getVisitDate)
                    .max(LocalDate::compareTo)
                    .orElseThrow();
            options.add(new LineReportAreaOption(
                    area.id(),
                    area.name(),
                    area.prefecture(),
                    area.city(),
                    visits.size(),
                    latestVisitDate
            ));
        }
        return List.copyOf(options);
    }

    private String targetLabel(LineReportGenerateCommand command) {
        if (command.reportType() == ReportType.SUMMARY
                || command.reportType() == ReportType.ALL) {
            return "전체 지역";
        }
        Map<Long, String> areaNames = areaService.findAll().stream()
                .collect(Collectors.toMap(
                        AreaSummaryResponse::id,
                        AreaSummaryResponse::name
                ));
        return command.areaIds().stream()
                .map(id -> areaNames.getOrDefault(id, "Area " + id))
                .collect(Collectors.joining(", "));
    }
}
