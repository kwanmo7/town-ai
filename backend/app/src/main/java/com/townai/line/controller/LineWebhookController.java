package com.townai.line.controller;

import com.townai.line.service.LineWebhookService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LINE Platform의 Webhook 요청을 수신하는 외부 Endpoint이다.
 *
 * <p>Request Body를 Byte 배열로 받아 JSON 변환 전 원문 서명 검증을 보장한다.
 * 지원 이벤트가 DB에 Commit되고 Dispatcher 전달까지 확인된 경우에만 Body 없는
 * {@code 200 OK}를 반환한다.</p>
 */
@RestController
@RequestMapping("/api/line/webhook")
public class LineWebhookController {

    private final LineWebhookService lineWebhookService;

    /**
     * LINE Webhook Controller를 생성한다.
     *
     * @param lineWebhookService Webhook 수신 Use Case
     */
    public LineWebhookController(LineWebhookService lineWebhookService) {
        this.lineWebhookService = lineWebhookService;
    }

    /**
     * LINE Webhook 요청을 검증·저장·전달한다.
     *
     * @param rawBody 가공하지 않은 Request Body
     * @param signature LINE Platform이 전달한 서명 Header
     * @return Body 없는 {@code 200 OK}
     */
    @PostMapping
    public ResponseEntity<Void> receive(
            @RequestBody(required = false) byte[] rawBody,
            @RequestHeader(
                    name = "X-Line-Signature",
                    required = false
            ) String signature
    ) {
        lineWebhookService.receive(rawBody, signature);
        return ResponseEntity.ok().build();
    }
}
