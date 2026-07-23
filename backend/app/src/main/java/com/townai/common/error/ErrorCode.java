package com.townai.common.error;

import org.springframework.http.HttpStatus;

/**
 * API에서 사용하는 오류 코드와 기본 HTTP 상태 및 사용자 메시지를 한곳에서 관리한다.
 *
 * <p>기본 메시지는 각 오류 코드에만 종속되므로 별도 문자열 Constants로 분리하지 않는다.
 * 향후 다국어가 필요하면 문자열 Constants 대신 메시지 키와 MessageSource를 사용한다.</p>
 */
public enum ErrorCode {

    /** JSON 문법 또는 필드 타입 때문에 요청 본문을 역직렬화할 수 없음. */
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "요청 본문을 읽을 수 없습니다."),

    /** Bean Validation 또는 요청 파라미터 형식 검증에 실패함. */
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "요청 값이 올바르지 않습니다."),

    /** Visit 조회 시작일이 종료일보다 늦음. */
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다."),

    /** Report 유형과 areaIds의 존재 여부·개수·중복 규칙이 맞지 않음. */
    INVALID_REPORT_TARGETS(HttpStatus.BAD_REQUEST, "리포트 생성 대상이 올바르지 않습니다."),

    /** 요청한 Report 유형을 지원하지 않음. */
    UNSUPPORTED_REPORT_TYPE(HttpStatus.BAD_REQUEST, "지원하지 않는 리포트 유형입니다."),

    /** LINE Webhook 요청의 서명 검증에 실패함. */
    INVALID_LINE_SIGNATURE(HttpStatus.UNAUTHORIZED, "LINE Webhook 서명이 올바르지 않습니다."),

    /** Area가 없거나 논리 삭제되어 활성 조회 대상이 아님. */
    AREA_NOT_FOUND(HttpStatus.NOT_FOUND, "지역을 찾을 수 없습니다."),

    /** Visit이 존재하지 않음. */
    VISIT_NOT_FOUND(HttpStatus.NOT_FOUND, "방문 기록을 찾을 수 없습니다."),

    /** Report 메타데이터가 존재하지 않음. */
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "리포트를 찾을 수 없습니다."),

    /** 해당 Endpoint가 요청한 HTTP Method를 지원하지 않음. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "지원하지 않는 HTTP Method입니다."),

    /** prefecture, city, name 조합이 기존 Area와 중복됨. */
    AREA_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 지역입니다."),

    /** 예상 가능한 오류로 분류되지 않은 서버 내부 실패. */
    INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "서버 내부 오류가 발생했습니다."),

    /** OpenAI 호출, 응답 추출 또는 AI 출력 교정에 실패함. */
    OPENAI_API_ERROR(HttpStatus.BAD_GATEWAY, "AI 처리 중 오류가 발생했습니다."),

    /** Report 본문 Storage의 읽기·쓰기·삭제에 실패함. */
    STORAGE_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "리포트 저장소를 사용할 수 없습니다."),

    /** LINE 이벤트를 비동기 처리 경로로 전달하지 못함. */
    LINE_EVENT_DISPATCH_ERROR(HttpStatus.SERVICE_UNAVAILABLE, "LINE 이벤트를 전달할 수 없습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /**
     * 오류 코드에 대응하는 HTTP 상태를 반환한다.
     *
     * @return 오류 응답에 사용할 HTTP 상태
     */
    public HttpStatus status() {
        return status;
    }

    /**
     * 오류 코드의 기본 사용자 메시지를 반환한다.
     *
     * @return 별도의 문구를 지정하지 않았을 때 사용할 사용자용 기본 메시지
     */
    public String message() {
        return message;
    }
}
