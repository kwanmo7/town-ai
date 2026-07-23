package com.townai.visit.parser;

/**
 * 자연어 Visit Parser 모델 호출을 기술 구현과 분리하는 Port이다.
 *
 * <p>Service는 OpenAI HTTP 세부사항을 알지 않고 이 계약을 통해 최초 요청과
 * 한 차례의 교정 요청을 수행한다.</p>
 */
public interface VisitParserAiClient {

    /**
     * 자연어 방문 평가를 strict JSON 출력으로 변환한다.
     *
     * @param input 현재 날짜, 자연어와 선택 가능한 활성 Area 목록
     * @param correctionInstruction 이전 출력의 검증 실패 사유. 최초 호출이면 {@code null}
     * @return Validator가 검사할 원본 JSON Text
     */
    String parse(VisitParserInput input, String correctionInstruction);
}
