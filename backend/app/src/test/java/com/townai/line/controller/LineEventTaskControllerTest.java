package com.townai.line.controller;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.processing.LineEventTaskResult;
import com.townai.line.processing.LineWebhookEventTaskService;
import com.townai.line.security.LineTaskRequestAuthenticator;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LineEventTaskControllerTest {

    private final LineTaskRequestAuthenticator authenticator =
            mock(LineTaskRequestAuthenticator.class);
    private final LineWebhookEventTaskService taskService =
            mock(LineWebhookEventTaskService.class);
    private final LineEventTaskController controller =
            new LineEventTaskController(authenticator, taskService);

    @Test
    void authenticatesBeforeProcessingAndReturnsNoContent() {
        when(taskService.process("event-1"))
                .thenReturn(LineEventTaskResult.ACKNOWLEDGED);

        var response = controller.process(
                "event-1",
                "Bearer token"
        );

        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        var ordered = inOrder(authenticator, taskService);
        ordered.verify(authenticator).authenticate("Bearer token");
        ordered.verify(taskService).process("event-1");
    }

    @Test
    void returnsServiceUnavailableForRetryResult() {
        when(taskService.process("event-1"))
                .thenReturn(LineEventTaskResult.RETRY);

        var response = controller.process("event-1", null);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );
    }

    @Test
    void doesNotProcessUnauthenticatedProductionRequest() {
        ApiException authenticationFailure = new ApiException(
                ErrorCode.INVALID_LINE_TASK_AUTHORIZATION
        );
        org.mockito.Mockito.doThrow(authenticationFailure)
                .when(authenticator)
                .authenticate(null);

        ApiException thrown = assertThrows(
                ApiException.class,
                () -> controller.process("event-1", null)
        );

        assertEquals(
                ErrorCode.INVALID_LINE_TASK_AUTHORIZATION,
                thrown.errorCode()
        );
        verify(taskService, never()).process("event-1");
    }
}
