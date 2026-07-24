package com.townai.line.controller;

import com.townai.line.processing.LineEventTaskResult;
import com.townai.line.processing.LineWebhookEventTaskService;
import com.townai.line.security.LineTaskRequestAuthenticator;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Local Dispatcher 또는 Cloud Tasks가 저장된 LINE 이벤트 처리를 요청하는
 * 내부 Endpoint이다.
 */
@RestController
@RequestMapping("/internal/tasks/line-events")
public class LineEventTaskController {

    private final LineTaskRequestAuthenticator requestAuthenticator;
    private final LineWebhookEventTaskService taskService;

    /**
     * LINE 내부 Task Controller를 생성한다.
     *
     * @param requestAuthenticator 실행 환경별 내부 호출 인증기
     * @param taskService 이벤트 점유 및 처리 Service
     */
    public LineEventTaskController(
            LineTaskRequestAuthenticator requestAuthenticator,
            LineWebhookEventTaskService taskService
    ) {
        this.requestAuthenticator = requestAuthenticator;
        this.taskService = taskService;
    }

    /**
     * 저장된 LINE 이벤트를 한 번 처리한다.
     *
     * @param webhookEventId 처리할 LINE Webhook Event ID
     * @param authorization 선택적인 OIDC Authorization Header
     * @return 승인 시 {@code 204}, 일시적인 실패 시 {@code 503}
     */
    @PostMapping("/{webhookEventId}")
    public ResponseEntity<Void> process(
            @PathVariable String webhookEventId,
            @RequestHeader(
                    name = "Authorization",
                    required = false
            ) String authorization
    ) {
        requestAuthenticator.authenticate(authorization);
        LineEventTaskResult result = taskService.process(webhookEventId);
        if (result == LineEventTaskResult.RETRY) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .build();
        }
        return ResponseEntity.noContent().build();
    }
}
