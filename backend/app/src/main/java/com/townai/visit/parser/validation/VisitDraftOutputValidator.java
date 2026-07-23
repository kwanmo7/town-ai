package com.townai.visit.parser.validation;

import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserInput;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

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
 * ISO 날짜, 활성 Area ID·이름 일치 여부를 다시 검사한다. 필수 값이 하나라도
 * 불확실하면 사용자 확인용 warning이 반드시 있어야 한다.</p>
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
    private static final Set<String> AREA_FIELDS = Set.of("id", "name");

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

        VisitDraftAreaResponse area = parseArea(root.get("area"), input);
        LocalDate visitDate = parseVisitDate(root.get("visitDate"));
        Integer atmosphereScore =
                parseScore(root.get("atmosphereScore"), "atmosphereScore");
        Integer infraScore = parseScore(root.get("infraScore"), "infraScore");
        Integer cleanScore = parseScore(root.get("cleanScore"), "cleanScore");
        Integer sizeScore = parseScore(root.get("sizeScore"), "sizeScore");
        Integer accessScore = parseScore(root.get("accessScore"), "accessScore");
        String memo = parseMemo(root.get("memo"));
        List<String> warnings = parseWarnings(root.get("warnings"));

        boolean requiredValueMissing = area == null
                || visitDate == null
                || atmosphereScore == null
                || infraScore == null
                || cleanScore == null
                || sizeScore == null
                || accessScore == null;
        if (requiredValueMissing && warnings.isEmpty()) {
            throw invalid("누락되거나 모호한 필드가 있지만 warning이 없습니다.");
        }

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
        JsonNode nameNode = areaNode.get("name");
        if (!idNode.isIntegralNumber() || !idNode.canConvertToLong()) {
            throw invalid("Parser Area ID가 정수가 아닙니다.");
        }
        if (!nameNode.isString() || nameNode.asString("").isBlank()) {
            throw invalid("Parser Area 이름이 비어 있습니다.");
        }

        Long areaId = idNode.longValue();
        String areaName = nameNode.asString();
        Map<Long, VisitParserInput.AreaInput> activeAreas = input.areas()
                .stream()
                .collect(Collectors.toMap(
                        VisitParserInput.AreaInput::id,
                        Function.identity()
                ));
        VisitParserInput.AreaInput matched = activeAreas.get(areaId);
        if (matched == null || !matched.name().equals(areaName)) {
            throw invalid("Parser Area ID 또는 이름이 입력 Area와 일치하지 않습니다.");
        }
        return new VisitDraftAreaResponse(matched.id(), matched.name());
    }

    private LocalDate parseVisitDate(JsonNode dateNode) {
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
