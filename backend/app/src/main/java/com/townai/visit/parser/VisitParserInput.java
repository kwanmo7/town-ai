package com.townai.visit.parser;

import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * Visit Parser Prompt에 전달하는 Backend 생성 입력이다.
 *
 * @param currentDate '오늘', '어제'와 같은 표현을 해석할 사용자 생활권 날짜
 * @param text 사용자가 입력한 자연어 방문 평가
 * @param areas Parser가 선택할 수 있는 활성 Area 목록
 * @param existingDraft 수정 모드에서 유지·변경할 기존 Draft. 최초 입력이면 {@code null}
 */
public record VisitParserInput(
        LocalDate currentDate,
        String text,
        List<AreaInput> areas,
        ExistingDraftInput existingDraft
) {

    /**
     * 최초 방문 평가를 파싱하는 입력을 생성한다.
     *
     * @param currentDate 상대 날짜 해석 기준
     * @param text 사용자 자연어 평가
     * @param areas 활성 Area 목록
     */
    public VisitParserInput(
            LocalDate currentDate,
            String text,
            List<AreaInput> areas
    ) {
        this(currentDate, text, areas, null);
    }

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

    /**
     * 부분 수정 시 Parser가 보존할 기존 Draft Snapshot이다.
     *
     * @param area 기존 또는 신규 Area 후보
     * @param visitDate 방문일
     * @param atmosphereScore 분위기 점수
     * @param infraScore 생활 인프라 점수
     * @param cleanScore 청결도 점수
     * @param sizeScore 넓은 집 가능성 점수
     * @param accessScore 접근성 점수
     * @param memo 메모
     */
    public record ExistingDraftInput(
            AreaInput area,
            LocalDate visitDate,
            Integer atmosphereScore,
            Integer infraScore,
            Integer cleanScore,
            Integer sizeScore,
            Integer accessScore,
            String memo
    ) {

        /**
         * 검증된 Visit Draft 응답을 Parser 입력 Snapshot으로 변환한다.
         *
         * @param response 기존 Draft 응답
         * @return 수정 모드용 Snapshot
         */
        public static ExistingDraftInput from(
                VisitDraftResponse response
        ) {
            VisitDraftAreaResponse area = response.area();
            return new ExistingDraftInput(
                    area == null
                            ? null
                            : new AreaInput(
                                    area.id(),
                                    area.name(),
                                    area.prefecture(),
                                    area.city(),
                                    area.station()
                            ),
                    response.visitDate(),
                    response.atmosphereScore(),
                    response.infraScore(),
                    response.cleanScore(),
                    response.sizeScore(),
                    response.accessScore(),
                    response.memo()
            );
        }
    }
}
