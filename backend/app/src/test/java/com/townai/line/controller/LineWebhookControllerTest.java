package com.townai.line.controller;

import com.townai.line.service.LineWebhookService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class LineWebhookControllerTest {

    @Test
    void forwardsRawBodyAndSignatureAndReturnsOk() {
        LineWebhookService service = mock(LineWebhookService.class);
        LineWebhookController controller =
                new LineWebhookController(service);
        byte[] body = "{\"events\":[]}".getBytes(StandardCharsets.UTF_8);

        var response = controller.receive(body, "signature");

        assertEquals(HttpStatus.OK, response.getStatusCode());
        verify(service).receive(body, "signature");
    }
}
