package com.townai.report.generation;

import com.townai.report.generation.ReportDataAssembler.AllInput;
import com.townai.report.generation.ReportDataAssembler.AreaInput;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI가 직접 작성한 Report Markdown의 구조와 사용자 표현 계약을 검증한다.
 *
 * <p>AREA와 ALL의 제목 순서, 대상 지역, 체크리스트 개수와 재택근무 사용자 전제를
 * 검사한다. 모든 Report 유형에 공통으로 내부 점수 필드명과 코드 블록 노출도
 * 차단한다.</p>
 */
@Component
public final class ReportMarkdownValidator {

    private static final Pattern INTERNAL_SCORE_FIELD_PATTERN = Pattern.compile(
            "(?i)(?<![a-z0-9_])"
                    + "(atmosphere(?:Score)?|infra(?:Score)?|clean(?:Score)?"
                    + "|size(?:Score)?|access(?:Score)?)"
                    + "(?![a-z0-9_])"
    );
    private static final Pattern DAILY_COMMUTE_PATTERN = Pattern.compile(
            "(?:출|퇴)\\s*근|통근"
    );
    private static final Pattern FATIGUE_RECORDING_PATTERN = Pattern.compile(
            "(?:피로(?:도|감)?)[^\\r\\n]{0,20}기록"
                    + "|기록[^\\r\\n]{0,20}(?:피로(?:도|감)?)"
    );

    String validateArea(AreaInput input, String output) {
        String markdown = output.strip();
        String areaName = input.area().name();
        List<String> requiredHeadings = List.of(
                "# " + areaName + " 지역 상세 분석",
                "## 평가 요약",
                "## 항목별 분석",
                "### 분위기",
                "### 생활 인프라",
                "### 청결도",
                "### 넓은 집 가능성",
                "### 접근성",
                "## 방문별 변화",
                "## 주요 장점",
                "## 주요 단점",
                "## 거주지 선택 시 고려사항",
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        );
        validateHeadingOrder(markdown, requiredHeadings);
        validateChecklistSection(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가",
                3,
                7
        );
        return markdown;
    }

    String validateAll(AllInput input, String output) {
        String markdown = output.strip();
        validateHeadingOrder(markdown, List.of(
                "# 전체 지역 분석 리포트",
                "## 전체 경향",
                "## 지역별 분석"
        ));
        int previousIndex = indexOfExactLine(
                markdown,
                "## 지역별 분석",
                0
        );
        for (var area : input.areas()) {
            String heading = "### " + area.name();
            int headingIndex = indexOfExactLine(
                    markdown,
                    heading,
                    previousIndex + 1
            );
            if (headingIndex < 0) {
                throw invalid("ALL 지역 제목이 누락되었습니다: " + area.name());
            }
            int nextAreaOrSection = markdown.length();
            int nextSection = indexOfExactLine(
                    markdown,
                    "## 항목별 주요 후보",
                    headingIndex + 1
            );
            if (nextSection >= 0) {
                nextAreaOrSection = nextSection;
            }
            if (area.displayOrder() < input.areas().size()) {
                String nextAreaHeading = "### "
                        + input.areas().get(area.displayOrder()).name();
                int nextArea = indexOfExactLine(
                        markdown,
                        nextAreaHeading,
                        headingIndex + 1
                );
                if (nextArea >= 0) {
                    nextAreaOrSection = Math.min(
                            nextAreaOrSection,
                            nextArea
                    );
                }
            }
            validateHeadingOrder(
                    markdown.substring(headingIndex, nextAreaOrSection),
                    List.of(
                            "#### 평가 요약",
                            "#### 주요 장점",
                            "#### 주요 단점",
                            "#### 고려사항"
                    )
            );
            previousIndex = headingIndex;
        }
        validateUniqueAreaHeadingCounts(markdown, input);
        validateHeadingOrder(markdown, List.of(
                "## 항목별 주요 후보",
                "## 우선순위별 후보",
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가"
        ), previousIndex);
        validateChecklistSection(
                markdown,
                "## 객관적으로 추가 확인할 사항",
                "## 종합 평가",
                5,
                10
        );
        return markdown;
    }

    void validateCommon(String markdown) {
        if (markdown.isBlank()
                || !markdown.stripLeading().startsWith("# ")) {
            throw invalid("Markdown 최상위 제목이 없습니다.");
        }
        if (markdown.contains("```")) {
            throw invalid("Markdown 결과에 코드 블록이 포함되었습니다.");
        }
        Matcher internalField =
                INTERNAL_SCORE_FIELD_PATTERN.matcher(markdown);
        if (internalField.find()) {
            throw invalid(
                    "사용자용 Report에 내부 점수 필드명 '"
                            + internalField.group()
                            + "'이 포함되었습니다. 분위기, 생활 인프라, 청결도, "
                            + "넓은 집 가능성 또는 접근성 중 대응하는 한글 표시명을 사용해야 합니다."
            );
        }
    }

    void validateChecklistText(String checklist) {
        if (DAILY_COMMUTE_PATTERN.matcher(checklist).find()) {
            throw invalid(
                    "재택근무 사용자에게 통근, 출근 또는 퇴근을 기본 확인 항목으로 "
                            + "제안할 수 없습니다. 도쿄 주요 지역 이동을 기준으로 작성해야 합니다."
            );
        }
        if (FATIGUE_RECORDING_PATTERN.matcher(checklist).find()) {
            throw invalid(
                    "체감 피로도를 기록하라고 요구하지 말고, "
                            + "도쿄 주요 지역까지 이동한 후 확인하고 후보 간 비교하도록 작성해야 합니다."
            );
        }
    }

    private void validateUniqueAreaHeadingCounts(
            String markdown,
            AllInput input
    ) {
        List<String> uniqueAreaNames = input.areas().stream()
                .map(area -> area.name())
                .distinct()
                .toList();
        for (String areaName : uniqueAreaNames) {
            String heading = "### " + areaName;
            long expectedCount = input.areas().stream()
                    .filter(area -> area.name().equals(areaName))
                    .count();
            if (countExactLines(markdown, heading) != expectedCount) {
                throw invalid(
                        "ALL 지역 제목의 개수가 입력과 다릅니다: " + areaName
                );
            }
        }
    }

    private void validateChecklistSection(
            String markdown,
            String sectionHeading,
            String nextHeading,
            int minimum,
            int maximum
    ) {
        int sectionStart = indexOfExactLine(markdown, sectionHeading, 0);
        int sectionEnd = indexOfExactLine(
                markdown,
                nextHeading,
                sectionStart + 1
        );
        if (sectionStart < 0 || sectionEnd < 0) {
            throw invalid("확인 체크리스트 구간을 찾을 수 없습니다.");
        }
        String checklist = markdown.substring(sectionStart, sectionEnd);
        long itemCount = checklist.lines()
                .map(String::stripLeading)
                .filter(line ->
                        line.startsWith("- ") || line.startsWith("* ")
                )
                .count();
        if (itemCount < minimum || itemCount > maximum) {
            throw invalid("확인 체크리스트 항목 수가 허용 범위를 벗어났습니다.");
        }
        validateChecklistText(checklist);
    }

    private void validateHeadingOrder(
            String markdown,
            List<String> headings
    ) {
        validateHeadingOrder(markdown, headings, -1);
    }

    private void validateHeadingOrder(
            String markdown,
            List<String> headings,
            int startingIndex
    ) {
        int previousIndex = startingIndex;
        for (String heading : headings) {
            int index = indexOfExactLine(
                    markdown,
                    heading,
                    previousIndex + 1
            );
            if (index < 0 || countExactLines(markdown, heading) != 1) {
                throw invalid(
                        "필수 Markdown 제목이 누락되었거나 중복되었습니다: "
                                + heading
                );
            }
            previousIndex = index;
        }
    }

    private int indexOfExactLine(
            String markdown,
            String heading,
            int fromIndex
    ) {
        int lineStart = 0;
        while (lineStart <= markdown.length()) {
            int lineFeed = markdown.indexOf('\n', lineStart);
            int lineEnd = lineFeed < 0 ? markdown.length() : lineFeed;
            String line = markdown.substring(
                    lineStart,
                    lineEnd
            ).stripTrailing();
            if (lineStart >= fromIndex && line.equals(heading)) {
                return lineStart;
            }
            if (lineFeed < 0) {
                break;
            }
            lineStart = lineFeed + 1;
        }
        return -1;
    }

    private int countExactLines(String markdown, String heading) {
        int count = 0;
        for (String line : markdown.lines().toList()) {
            if (line.stripTrailing().equals(heading)) {
                count++;
            }
        }
        return count;
    }

    private InvalidReportOutputException invalid(String message) {
        return new InvalidReportOutputException(message);
    }
}
