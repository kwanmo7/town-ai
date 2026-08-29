package com.townai.visit.service.impl;

import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserAiClient;
import com.townai.visit.parser.VisitParserInput;
import com.townai.visit.parser.validation.InvalidVisitDraftOutputException;
import com.townai.visit.parser.validation.VisitDraftOutputValidator;
import com.townai.visit.service.VisitDraftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

/**
 * 자연어 Visit 초안 생성 Use Case를 조정한다.
 *
 * <p>현재 날짜와 활성 Area 목록으로 Parser 입력을 만들고, AI 원본 출력을 별도
 * Validator에 전달한다. 출력 계약 위반 시 검증 사유를 포함해 정확히 한 번 교정
 * 요청하며, 두 번째 출력도 실패하면 외부 AI 오류로 변환한다. 신규 Area의 위치가
 * 비어 있으면 저장을 막기 전에 별도의 Best-effort 위치 보완 요청을 한 번 수행한다.
 * 이 과정에서는 Visit Repository를 사용하지 않으므로 어떤 초안도 저장되지
 * 않는다.</p>
 */
@Service
public class VisitDraftServiceImpl implements VisitDraftService {

    private static final Logger log =
            LoggerFactory.getLogger(VisitDraftServiceImpl.class);

    private final AreaRepository areaRepository;
    private final VisitParserAiClient parserAiClient;
    private final VisitDraftOutputValidator outputValidator;
    private final Clock clock;
    private final ZoneId userTimeZone;

    /**
     * 자연어 Visit 초안 생성 Service를 구성한다.
     *
     * @param areaRepository Parser 후보로 제공할 활성 Area Repository
     * @param parserAiClient 자연어 Parser 모델 호출 Port
     * @param outputValidator AI 원본 JSON 검증기
     * @param clock 현재 시각 기준
     * @param userTimeZone 상대 날짜를 해석할 사용자 생활권 시간대
     */
    public VisitDraftServiceImpl(
            AreaRepository areaRepository,
            VisitParserAiClient parserAiClient,
            VisitDraftOutputValidator outputValidator,
            Clock clock,
            ZoneId userTimeZone
    ) {
        this.areaRepository = areaRepository;
        this.parserAiClient = parserAiClient;
        this.outputValidator = outputValidator;
        this.clock = clock;
        this.userTimeZone = userTimeZone;
    }

    @Override
    public VisitDraftResponse create(VisitDraftRequest request) {
        VisitDraftResponse parsed = parse(createInput(
                request.text().strip(),
                null,
                toSelectedScores(request)
        ));
        return applyScoreOverrides(parsed, request);
    }

    @Override
    public VisitDraftResponse revise(
            VisitDraftResponse existingDraft,
            VisitDraftRequest request
    ) {
        return parse(createInput(
                request.text().strip(),
                VisitParserInput.ExistingDraftInput.from(existingDraft),
                null
        ));
    }

    private VisitDraftResponse parse(VisitParserInput input) {
        VisitDraftResponse response = parseWithContractCorrection(input);
        if (!needsNewAreaLocationEnrichment(input, response)) {
            return response;
        }
        return enrichNewAreaLocation(input, response);
    }

    private VisitDraftResponse parseWithContractCorrection(
            VisitParserInput input
    ) {
        try {
            return validate(parserAiClient.parse(input, null), input);
        } catch (InvalidVisitDraftOutputException firstFailure) {
            log.warn(
                    "Visit Parser output validation failed. Retrying correction once. reason={}",
                    firstFailure.getMessage()
            );
            String correctedOutput = parserAiClient.parse(
                    input,
                    firstFailure.getMessage()
            );
            try {
                return validate(correctedOutput, input);
            } catch (InvalidVisitDraftOutputException secondFailure) {
                log.error(
                        "Corrected Visit Parser output validation failed. reason={}",
                        secondFailure.getMessage()
                );
                throw new ApiException(ErrorCode.OPENAI_API_ERROR);
            }
        }
    }

    private VisitDraftResponse enrichNewAreaLocation(
            VisitParserInput input,
            VisitDraftResponse original
    ) {
        try {
            VisitDraftResponse enriched = validate(
                    parserAiClient.parse(
                            input,
                            "신규 Area의 도도부현 또는 시구정촌이 비어 있습니다. "
                                    + "사용자가 행정구역을 직접 적지 않았더라도 "
                                    + "지역명이나 역명으로 명확히 특정할 수 있으면 "
                                    + "일반적인 일본 지리 지식으로 prefecture와 city를 "
                                    + "보완하십시오. 문자열 'null', '광역권' 같은 "
                                    + "자리표시자는 사용하지 마십시오. 동명 지역 등으로 "
                                    + "확정할 수 없을 때만 JSON null을 유지하십시오."
                    ),
                    input
            );
            return mergeEnrichedArea(original, enriched);
        } catch (InvalidVisitDraftOutputException | ApiException exception) {
            log.warn(
                    "Visit Parser location enrichment failed. Keeping reviewable draft. reason={}",
                    exception.getMessage()
            );
            return original;
        }
    }

    private VisitDraftResponse mergeEnrichedArea(
            VisitDraftResponse original,
            VisitDraftResponse enriched
    ) {
        VisitDraftAreaResponse originalArea = original.area();
        VisitDraftAreaResponse enrichedArea = enriched.area();
        if (originalArea == null
                || enrichedArea == null
                || !samePlaceName(originalArea.name(), enrichedArea.name())
                || !hasText(enrichedArea.prefecture())
                || !hasText(enrichedArea.city())) {
            return original;
        }

        VisitDraftAreaResponse mergedArea = enrichedArea.registered()
                ? enrichedArea
                : new VisitDraftAreaResponse(
                        null,
                        originalArea.name(),
                        enrichedArea.prefecture(),
                        enrichedArea.city(),
                        hasText(enrichedArea.station())
                                ? enrichedArea.station()
                                : originalArea.station()
                );
        return new VisitDraftResponse(
                mergedArea,
                original.visitDate(),
                original.atmosphereScore(),
                original.infraScore(),
                original.cleanScore(),
                original.sizeScore(),
                original.accessScore(),
                original.memo(),
                enriched.warnings()
        );
    }

    private boolean samePlaceName(String first, String second) {
        return normalizePlaceName(first).equals(normalizePlaceName(second));
    }

    private String normalizePlaceName(String value) {
        if (value == null) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private boolean needsNewAreaLocationEnrichment(
            VisitParserInput input,
            VisitDraftResponse response
    ) {
        return input.existingDraft() == null
                && response.area() != null
                && !response.area().registered()
                && !hasCompleteNewAreaLocation(response);
    }

    private boolean hasCompleteNewAreaLocation(
            VisitDraftResponse response
    ) {
        return response.area() != null
                && hasText(response.area().prefecture())
                && hasText(response.area().city());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /**
     * 명시적인 Web 선택값은 AI 출력보다 우선한다. 다섯 점수가 모두 전달된 경우
     * Parser가 남긴 점수 누락 경고도 이미 해결된 것으로 제거한다.
     */
    private VisitDraftResponse applyScoreOverrides(
            VisitDraftResponse parsed,
            VisitDraftRequest request
    ) {
        if (!hasAnyScoreOverride(request)) {
            return parsed;
        }

        List<String> warnings = hasAllScoreOverrides(request)
                ? parsed.warnings().stream()
                        .filter(warning -> !isMissingScoreWarning(warning))
                        .toList()
                : parsed.warnings();

        return new VisitDraftResponse(
                parsed.area(),
                parsed.visitDate(),
                override(request.atmosphereScore(), parsed.atmosphereScore()),
                override(request.infraScore(), parsed.infraScore()),
                override(request.cleanScore(), parsed.cleanScore()),
                override(request.sizeScore(), parsed.sizeScore()),
                override(request.accessScore(), parsed.accessScore()),
                parsed.memo(),
                warnings
        );
    }

    private boolean hasAnyScoreOverride(VisitDraftRequest request) {
        return request.atmosphereScore() != null
                || request.infraScore() != null
                || request.cleanScore() != null
                || request.sizeScore() != null
                || request.accessScore() != null;
    }

    private boolean hasAllScoreOverrides(VisitDraftRequest request) {
        return request.atmosphereScore() != null
                && request.infraScore() != null
                && request.cleanScore() != null
                && request.sizeScore() != null
                && request.accessScore() != null;
    }

    private boolean isMissingScoreWarning(String warning) {
        return warning != null
                && (warning.contains("입력되지") || warning.contains("누락"))
                && (warning.contains("점수")
                || warning.contains("Score")
                || warning.contains("score"));
    }

    private Integer override(Integer selected, Integer parsed) {
        return selected != null ? selected : parsed;
    }

    private VisitParserInput.SelectedScoresInput toSelectedScores(
            VisitDraftRequest request
    ) {
        if (!hasAnyScoreOverride(request)) {
            return null;
        }
        return new VisitParserInput.SelectedScoresInput(
                request.atmosphereScore(),
                request.infraScore(),
                request.cleanScore(),
                request.sizeScore(),
                request.accessScore()
        );
    }

    private VisitDraftResponse validate(
            String output,
            VisitParserInput input
    ) {
        return input.existingDraft() == null
                ? outputValidator.validate(output, input)
                : outputValidator.validateRevision(output, input);
    }

    /**
     * Parser가 임의의 Area를 만들지 못하도록 현재 활성 Area만 입력 후보로 제공한다.
     */
    private VisitParserInput createInput(
            String text,
            VisitParserInput.ExistingDraftInput existingDraft,
            VisitParserInput.SelectedScoresInput selectedScores
    ) {
        List<VisitParserInput.AreaInput> areas =
                areaRepository.findAllByDeletedAtIsNullOrderByIdAsc()
                        .stream()
                        .map(area -> new VisitParserInput.AreaInput(
                                area.getId(),
                                area.getName(),
                                area.getPrefecture(),
                                area.getCity(),
                                area.getStation()
                        ))
                        .toList();
        return new VisitParserInput(
                LocalDate.ofInstant(clock.instant(), userTimeZone),
                text,
                areas,
                existingDraft,
                selectedScores
        );
    }
}
