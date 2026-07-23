package com.townai.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * 모든 REST API 오류에 사용하는 공통 응답 형식이다.
 *
 * @param timestamp 오류가 응답으로 변환된 UTC 시각
 * @param status HTTP 상태 코드의 숫자 값
 * @param code 클라이언트 분기 처리에 사용하는 안정적인 오류 코드
 * @param message 사용자에게 표시할 수 있는 오류 설명
 * @param path 오류가 발생한 요청 경로
 * @param errors 필드 단위 검증 오류. 검증 오류가 아니면 빈 목록
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        List<ValidationError> errors
) {

    /**
     * 검증 오류 목록을 불변 목록으로 정규화한다.
     */
    public ErrorResponse {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    /**
     * 요청 필드 하나에 대한 검증 실패 내용이다.
     *
     * @param field 실패한 필드 또는 파라미터 경로
     * @param reason 검증 실패 이유
     */
    public record ValidationError(String field, String reason) {
    }
}
