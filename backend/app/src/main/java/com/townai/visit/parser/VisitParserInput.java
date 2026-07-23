package com.townai.visit.parser;

import java.time.LocalDate;
import java.util.List;

/**
 * Visit Parser Prompt에 전달하는 Backend 생성 입력이다.
 *
 * @param currentDate '오늘', '어제'와 같은 표현을 해석할 사용자 생활권 날짜
 * @param text 사용자가 입력한 자연어 방문 평가
 * @param areas Parser가 선택할 수 있는 활성 Area 목록
 */
public record VisitParserInput(
        LocalDate currentDate,
        String text,
        List<AreaInput> areas
) {

    /**
     * Parser에 제공하는 활성 Area 후보이다.
     *
     * @param id Backend가 최종 검증할 Area ID
     * @param name 동네 이름
     * @param prefecture 도도부현 이름
     * @param city 시구정촌 이름
     * @param station 인접 역 이름
     */
    public record AreaInput(
            Long id,
            String name,
            String prefecture,
            String city,
            String station
    ) {
    }
}
