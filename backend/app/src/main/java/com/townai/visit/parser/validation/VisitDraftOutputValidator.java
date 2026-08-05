package com.townai.visit.parser.validation;

import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserInput;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI Parser의 원본 JSON을 신뢰 가능한 Visit 초안으로 변환한다.
 *
 * <p>strict Schema 사용 여부와 관계없이 Backend에서 필드 집합, 타입, 점수 범위,
 * ISO 날짜, 활성 Area 정보 또는 신규 Area 위치 후보를 다시 검사한다. 필수 값이
 * 하나라도 불확실하거나 신규 후보이면 사용자 확인용 warning이 반드시 있어야
 * 한다.</p>
 */
@Component
public class VisitDraftOutputValidator {

    private static final Set<String> OUTPUT_FIELDS = Set.of(
            "area",
            "visitDate",
            "atmosphereScore",
            "infraScore",
            "cleanScore",
            "sizeScore",
            "accessScore",
            "memo",
            "warnings"
    );
    private static final Set<String> REVISION_OUTPUT_FIELDS = Set.of(
            "area",
            "visitDate",
            "atmosphereScore",
            "infraScore",
            "cleanScore",
            "sizeScore",
            "accessScore",
            "memo",
            "warnings",
            "changedFields"
    );
    private static final Set<String> REVISION_FIELDS = Set.of(
            "area",
            "visitDate",
            "atmosphereScore",
            "infraScore",
            "cleanScore",
            "sizeScore",
            "accessScore",
            "memo"
    );
    private static final Set<String> AREA_FIELDS = Set.of(
            "id",
            "name",
            "prefecture",
            "city",
            "station"
    );
    private static final Set<String> NULL_LIKE_AREA_VALUES = Set.of(
            "null",
            "none",
            "unknown",
            "없음",
            "미입력",
            "미확정",
            "미상",
            "불명",
            "-"
    );
    private static final Set<String> GENERIC_PREFECTURE_VALUES = Set.of(
            "광역권",
            "도도부현",
            "현",
            "도",
            "부"
    );
    private static final Set<String> GENERIC_CITY_VALUES = Set.of(
            "시구정촌",
            "행정구역",
            "도시",
            "시",
            "구"
    );

    private final ObjectMapper objectMapper;

    /**
     * Parser 출력 Validator를 생성한다.
     *
     * @param objectMapper 원본 JSON Text를 읽을 ObjectMapper
     */
    public VisitDraftOutputValidator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Parser 출력을 읽고 모든 계약을 만족하는 불변 초안을 만든다.
     *
     * @param output AI가 반환한 원본 JSON Text
     * @param input AI 호출에 사용한 현재 날짜와 활성 Area 후보
     * @return 검증된 Visit 초안
     * @throws InvalidVisitDraftOutputException JSON 구조나 값이 계약을 위반한 경우
     */
    public VisitDraftResponse validate(
            String output,
            VisitParserInput input
    ) {
        JsonNode root = readOutput(output);
        requireExactFields(root, OUTPUT_FIELDS, "Parser 최상위");
        return parseDraft(root, input);
    }

    /**
     * 수정 Parser 출력에서 명시된 필드만 기존 Draft에 반영한다.
     *
     * <p>AI가 반환한 완성본에서 {@code changedFields}에 없는 값은 사용하지 않는다.
     * 따라서 사용자가 언급하지 않은 기존 값은 모델 출력과 무관하게 보존된다.</p>
     *
     * @param output AI가 반환한 수정 모드 원본 JSON Text
     * @param input 기존 Draft가 포함된 Parser 입력
     * @return Backend가 결정적으로 병합하고 검증한 새 Draft
     * @throws InvalidVisitDraftOutputException 수정 출력 계약을 위반한 경우
     */
    public VisitDraftResponse validateRevision(
            String output,
            VisitParserInput input
    ) {
        if (input.existingDraft() == null) {
            throw invalid("수정 Parser 입력에 기존 Draft가 없습니다.");
        }
        JsonNode root = readOutput(output);
        requireExactFields(root, REVISION_OUTPUT_FIELDS, "수정 Parser 최상위");
        Set<String> changedFields = parseChangedFields(
                root.get("changedFields")
        );
        VisitDraftResponse parsed = parseDraftFields(root, input);
        VisitDraftResponse existing = existingDraft(input.existingDraft());
        VisitDraftResponse merged = new VisitDraftResponse(
                choose(changedFields, "area", parsed.area(), existing.area()),
                choose(
                        changedFields,
                        "visitDate",
                        parsed.visitDate(),
                        existing.visitDate()
                ),
                choose(
                        changedFields,
                        "atmosphereScore",
                        parsed.atmosphereScore(),
                        existing.atmosphereScore()
                ),
                choose(
                        changedFields,
                        "infraScore",
                        parsed.infraScore(),
                        existing.infraScore()
                ),
                choose(
                        changedFields,
                        "cleanScore",
                        parsed.cleanScore(),
                        existing.cleanScore()
                ),
                choose(
                        changedFields,
                        "sizeScore",
                        parsed.sizeScore(),
                        existing.sizeScore()
                ),
                choose(
                        changedFields,
                        "accessScore",
                        parsed.accessScore(),
                        existing.accessScore()
                ),
                choose(changedFields, "memo", parsed.memo(), existing.memo()),
                parsed.warnings()
        );
        requireReviewWarnings(merged);
        return merged;
    }

    private VisitDraftResponse parseDraft(
            JsonNode root,
            VisitParserInput input
    ) {
        VisitDraftResponse response = parseDraftFields(root, input);
        requireReviewWarnings(response);
        return response;
    }

    private VisitDraftResponse parseDraftFields(
            JsonNode root,
            VisitParserInput input
    ) {

        VisitDraftAreaResponse area = parseArea(root.get("area"), input);
        LocalDate visitDate = parseVisitDate(
                root.get("visitDate"),
                input.currentDate()
        );
        Integer atmosphereScore =
                parseScore(root.get("atmosphereScore"), "atmosphereScore");
        Integer infraScore = parseScore(root.get("infraScore"), "infraScore");
        Integer cleanScore = parseScore(root.get("cleanScore"), "cleanScore");
        Integer sizeScore = parseScore(root.get("sizeScore"), "sizeScore");
        Integer accessScore = parseScore(root.get("accessScore"), "accessScore");
        String memo = parseMemo(root.get("memo"));
        List<String> warnings = parseWarnings(root.get("warnings"));

        return new VisitDraftResponse(
                area,
                visitDate,
                atmosphereScore,
                infraScore,
                cleanScore,
                sizeScore,
                accessScore,
                memo,
                warnings
        );
    }

    private void requireReviewWarnings(VisitDraftResponse response) {
        boolean requiredValueMissing = response.area() == null
                || response.visitDate() == null
                || response.atmosphereScore() == null
                || response.infraScore() == null
                || response.cleanScore() == null
                || response.sizeScore() == null
                || response.accessScore() == null;
        boolean newAreaNeedsConfirmation = response.area() != null
                && !response.area().registered();
        if ((requiredValueMissing || newAreaNeedsConfirmation)
                && response.warnings().isEmpty()) {
            throw invalid("누락되거나 모호한 필드가 있지만 warning이 없습니다.");
        }
    }

    private JsonNode readOutput(String output) {
        if (output == null || output.isBlank()) {
            throw invalid("Parser 응답 본문이 비어 있습니다.");
        }
        try {
            JsonNode root = objectMapper.readTree(output);
            if (root == null || !root.isObject()) {
                throw invalid("Parser 응답이 JSON 객체가 아닙니다.");
            }
            return root;
        } catch (JacksonException exception) {
            throw invalid("Parser 응답 JSON을 읽을 수 없습니다.");
        }
    }

    private VisitDraftAreaResponse parseArea(
            JsonNode areaNode,
            VisitParserInput input
    ) {
        if (areaNode.isNull()) {
            return null;
        }
        requireExactFields(areaNode, AREA_FIELDS, "Parser Area");
        JsonNode idNode = areaNode.get("id");
        Long areaId = parseNullableAreaId(idNode);
        String areaName = parseRequiredAreaText(
                areaNode.get("name"),
                "name",
                25
        );
        String prefecture = parseNullableAreaText(
                areaNode.get("prefecture"),
                "prefecture",
                20
        );
        String city = parseNullableAreaText(
                areaNode.get("city"),
                "city",
                20
        );
        String station = parseNullableAreaText(
                areaNode.get("station"),
                "station",
                50
        );
        if (areaId == null) {
            VisitDraftAreaResponse existing = findExistingAreaByLocation(
                    areaName,
                    prefecture,
                    city,
                    input
            );
            if (existing != null) {
                return existing;
            }
            return new VisitDraftAreaResponse(
                    null,
                    areaName,
                    prefecture,
                    city,
                    station
            );
        }

        Map<Long, VisitParserInput.AreaInput> activeAreas = input.areas()
                .stream()
                .collect(Collectors.toMap(
                        VisitParserInput.AreaInput::id,
                        Function.identity()
                ));
        VisitParserInput.AreaInput matched = activeAreas.get(areaId);
        if (matched == null || !matched.name().equals(areaName)) {
            throw invalid("Parser Area 정보가 입력 Area와 일치하지 않습니다.");
        }
        return new VisitDraftAreaResponse(
                matched.id(),
                matched.name(),
                matched.prefecture(),
                matched.city(),
                matched.station()
        );
    }

    private Long parseNullableAreaId(JsonNode idNode) {
        if (idNode.isNull()) {
            return null;
        }
        if (!idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
            throw invalid("Parser Area ID가 정수 또는 null이 아닙니다.");
        }
        long id = idNode.longValue();
        if (id <= 0) {
            throw invalid("Parser Area ID가 양수가 아닙니다.");
        }
        return id;
    }

    private String parseRequiredAreaText(
            JsonNode node,
            String fieldName,
            int maximumLength
    ) {
        String value = parseNullableAreaText(node, fieldName, maximumLength);
        if (value == null) {
            throw invalid("Parser Area " + fieldName + "이 비어 있습니다.");
        }
        return value;
    }

    private String parseNullableAreaText(
            JsonNode node,
            String fieldName,
            int maximumLength
    ) {
        if (node.isNull()) {
            return null;
        }
        if (!node.isString()) {
            throw invalid("Parser Area " + fieldName + "이 문자열이 아닙니다.");
        }
        String value = node.asString().strip();
        if (value.isEmpty()) {
            throw invalid("Parser Area " + fieldName + "이 빈 문자열입니다.");
        }
        if (isAreaPlaceholder(fieldName, value)) {
            return null;
        }
        if (value.length() > maximumLength) {
            throw invalid(
                    "Parser Area " + fieldName + "이 최대 길이를 초과했습니다."
            );
        }
        return value;
    }

    private boolean isAreaPlaceholder(
            String fieldName,
            String value
    ) {
        String normalized = normalizePlaceText(value);
        if (NULL_LIKE_AREA_VALUES.contains(normalized)) {
            return true;
        }
        return switch (fieldName) {
            case "prefecture" ->
                    GENERIC_PREFECTURE_VALUES.contains(normalized);
            case "city" -> GENERIC_CITY_VALUES.contains(normalized);
            default -> false;
        };
    }

    private LocalDate parseVisitDate(
            JsonNode dateNode,
            LocalDate currentDate
    ) {
        if (dateNode.isNull()) {
            return null;
        }
        if (!dateNode.isString()) {
            throw invalid("visitDate가 문자열이 아닙니다.");
        }
        String value = dateNode.asString();
        try {
            LocalDate date = LocalDate.parse(value);
            if (!date.toString().equals(value)) {
                throw invalid("visitDate가 yyyy-MM-dd 형식이 아닙니다.");
            }
            if (date.isAfter(currentDate)) {
                throw invalid(
                        "visitDate가 미래 날짜입니다. null과 warning으로 교정해야 합니다."
                );
            }
            return date;
        } catch (DateTimeParseException exception) {
            throw invalid("visitDate가 유효한 yyyy-MM-dd 날짜가 아닙니다.");
        }
    }

    private Integer parseScore(JsonNode scoreNode, String fieldName) {
        if (scoreNode.isNull()) {
            return null;
        }
        if (!scoreNode.isIntegralNumber() || !scoreNode.canConvertToInt()) {
            throw invalid(fieldName + "가 정수가 아닙니다.");
        }
        int score = scoreNode.intValue();
        if (score < 0 || score > 10) {
            throw invalid(fieldName + "가 0 이상 10 이하가 아닙니다.");
        }
        return score;
    }

    private String parseMemo(JsonNode memoNode) {
        if (memoNode.isNull()) {
            return null;
        }
        if (!memoNode.isString()) {
            throw invalid("memo가 문자열이 아닙니다.");
        }
        String memo = memoNode.asString().strip();
        return memo.isEmpty() ? null : memo;
    }

    private List<String> parseWarnings(JsonNode warningsNode) {
        if (!warningsNode.isArray()) {
            throw invalid("warnings가 배열이 아닙니다.");
        }
        List<String> warnings = new ArrayList<>();
        for (JsonNode warningNode : warningsNode) {
            if (!warningNode.isString()
                    || warningNode.asString("").isBlank()) {
                throw invalid(
                        "warnings에 빈 문자열 또는 문자열이 아닌 값이 있습니다."
                );
            }
            warnings.add(warningNode.asString().strip());
        }
        return List.copyOf(warnings);
    }

    private VisitDraftAreaResponse findExistingAreaByLocation(
            String areaName,
            String prefecture,
            String city,
            VisitParserInput input
    ) {
        if (prefecture == null || city == null) {
            return null;
        }
        String normalizedName = normalizePlaceText(areaName);
        String normalizedPrefecture = normalizePlaceText(prefecture);
        String normalizedCity = normalizePlaceText(city);
        List<VisitParserInput.AreaInput> matches = input.areas().stream()
                .filter(area -> normalizePlaceText(area.name())
                        .equals(normalizedName))
                .filter(area -> normalizePlaceText(area.prefecture())
                        .equals(normalizedPrefecture))
                .filter(area -> normalizePlaceText(area.city())
                        .equals(normalizedCity))
                .toList();
        return matches.size() == 1
                ? registeredArea(matches.getFirst())
                : null;
    }

    private VisitDraftAreaResponse registeredArea(
            VisitParserInput.AreaInput area
    ) {
        return new VisitDraftAreaResponse(
                area.id(),
                area.name(),
                area.prefecture(),
                area.city(),
                area.station()
        );
    }

    private String normalizePlaceText(String value) {
        return Normalizer.normalize(value, Normalizer.Form.NFKC)
                .toLowerCase(java.util.Locale.ROOT)
                .replaceAll("\\s+", "");
    }

    private Set<String> parseChangedFields(JsonNode changedFieldsNode) {
        if (changedFieldsNode == null || !changedFieldsNode.isArray()) {
            throw invalid("changedFields가 배열이 아닙니다.");
        }
        Set<String> changedFields = new java.util.LinkedHashSet<>();
        for (JsonNode fieldNode : changedFieldsNode) {
            if (!fieldNode.isString()
                    || !REVISION_FIELDS.contains(fieldNode.asString())) {
                throw invalid("changedFields에 지원하지 않는 필드가 있습니다.");
            }
            if (!changedFields.add(fieldNode.asString())) {
                throw invalid("changedFields에 중복 필드가 있습니다.");
            }
        }
        return Set.copyOf(changedFields);
    }

    private VisitDraftResponse existingDraft(
            VisitParserInput.ExistingDraftInput existing
    ) {
        VisitParserInput.AreaInput area = existing.area();
        return new VisitDraftResponse(
                area == null
                        ? null
                        : new VisitDraftAreaResponse(
                                area.id(),
                                area.name(),
                                area.prefecture(),
                                area.city(),
                                area.station()
                        ),
                existing.visitDate(),
                existing.atmosphereScore(),
                existing.infraScore(),
                existing.cleanScore(),
                existing.sizeScore(),
                existing.accessScore(),
                existing.memo(),
                List.of()
        );
    }

    private <T> T choose(
            Set<String> changedFields,
            String field,
            T changedValue,
            T existingValue
    ) {
        return changedFields.contains(field) ? changedValue : existingValue;
    }

    private void requireExactFields(
            JsonNode node,
            Set<String> expectedFields,
            String target
    ) {
        if (node == null || !node.isObject()) {
            throw invalid(target + "가 JSON 객체가 아닙니다.");
        }
        for (String field : expectedFields) {
            if (!node.has(field)) {
                throw invalid(
                        target + " 필수 필드가 누락되었습니다: " + field
                );
            }
        }
        if (node.size() != expectedFields.size()) {
            throw invalid(target + "에 정의되지 않은 필드가 있습니다.");
        }
    }

    private InvalidVisitDraftOutputException invalid(String message) {
        return new InvalidVisitDraftOutputException(message);
    }
}
