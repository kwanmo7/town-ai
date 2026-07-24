package com.townai.report.generation;

import com.townai.report.generation.ReportDataAssembler.AllAreaInput;
import com.townai.report.generation.ReportDataAssembler.AllInput;
import com.townai.report.generation.ReportDataAssembler.AreaInfo;
import com.townai.report.generation.ReportDataAssembler.AreaInput;
import com.townai.report.generation.ReportDataAssembler.AreaStatistics;
import com.townai.report.generation.ReportDataAssembler.ScoreAverages;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ReportMarkdownValidatorTest {

    private final ReportMarkdownValidator validator =
            new ReportMarkdownValidator();

    @Test
    void acceptsAreaMarkdownWithRequiredStructureAndChecklist() {
        AreaInput input = new AreaInput(
                new AreaInfo(
                        1L,
                        "센터미나미",
                        "가나가와현",
                        "요코하마시",
                        "센터미나미역"
                ),
                new AreaStatistics(
                        0,
                        emptyScores()
                ),
                List.of()
        );
        String markdown = """
                # 센터미나미 지역 상세 분석
                ## 평가 요약
                요약
                ## 항목별 분석
                ### 분위기
                분석
                ### 생활 인프라
                분석
                ### 청결도
                분석
                ### 넓은 집 가능성
                분석
                ### 접근성
                분석
                ## 방문별 변화
                변화
                ## 주요 장점
                장점
                ## 주요 단점
                단점
                ## 거주지 선택 시 고려사항
                고려
                ## 객관적으로 추가 확인할 사항
                - 도쿄 주요 지역까지 이동 편의를 확인
                - 주거비를 비교
                - 야간 환경을 확인
                ## 종합 평가
                평가
                """;

        assertEquals(markdown.strip(), validator.validateArea(input, markdown));
    }

    @Test
    void rejectsAllMarkdownWhenAnInputAreaHeadingIsMissing() {
        AllInput input = new AllInput(List.of(
                allArea(1, 1L, "센터미나미"),
                allArea(2, 2L, "타마플라자")
        ));
        String markdown = """
                # 전체 지역 분석 리포트
                ## 전체 경향
                경향
                ## 지역별 분석
                ### 센터미나미
                #### 평가 요약
                요약
                #### 주요 장점
                장점
                #### 주요 단점
                단점
                #### 고려사항
                고려
                ## 항목별 주요 후보
                후보
                ## 우선순위별 후보
                후보
                ## 객관적으로 추가 확인할 사항
                - 확인 1
                - 확인 2
                - 확인 3
                - 확인 4
                - 확인 5
                ## 종합 평가
                평가
                """;

        assertThrows(
                InvalidReportOutputException.class,
                () -> validator.validateAll(input, markdown)
        );
    }

    private AllAreaInput allArea(
            int displayOrder,
            Long id,
            String name
    ) {
        return new AllAreaInput(
                displayOrder,
                id,
                name,
                "가나가와현",
                "요코하마시",
                null,
                0,
                emptyScores(),
                List.of()
        );
    }

    private ScoreAverages emptyScores() {
        return new ScoreAverages(null, null, null, null, null);
    }
}
