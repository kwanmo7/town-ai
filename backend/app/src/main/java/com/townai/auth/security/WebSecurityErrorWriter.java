package com.townai.auth.security;

import com.townai.common.error.ErrorCode;
import com.townai.common.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Controller에 도달하기 전 발생한 Web 인증 실패를 공통 오류 JSON으로 변환한다.
 */
@Component
public class WebSecurityErrorWriter {

    private static final MediaType JSON_UTF8 = new MediaType(
            MediaType.APPLICATION_JSON,
            StandardCharsets.UTF_8
    );

    private final ObjectMapper objectMapper;
    private final Clock clock;

    /**
     * 인증 오류 작성기를 생성한다.
     *
     * @param objectMapper 공통 오류 응답 직렬화 도구
     * @param clock 오류 발생 UTC 시각을 생성할 Clock
     */
    public WebSecurityErrorWriter(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    /**
     * 지정한 오류 코드로 HTTP 응답을 종료한다.
     *
     * @param request 인증에 실패한 요청
     * @param response 작성할 HTTP 응답
     * @param errorCode 사용자에게 노출할 안정적인 인증 오류 코드
     * @throws IOException 응답 본문 작성에 실패한 경우
     */
    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.status().value());
        response.setContentType(JSON_UTF8.toString());
        ErrorResponse body = new ErrorResponse(
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS),
                errorCode.status().value(),
                errorCode.name(),
                errorCode.message(),
                request.getRequestURI(),
                List.of()
        );
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
