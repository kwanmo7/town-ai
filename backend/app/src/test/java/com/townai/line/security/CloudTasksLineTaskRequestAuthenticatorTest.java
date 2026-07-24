package com.townai.line.security;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.config.LineTaskProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudTasksLineTaskRequestAuthenticatorTest {

    private static final String SERVICE_ACCOUNT =
            "line-task@town-ai.iam.gserviceaccount.com";

    private final LineOidcTokenVerifier tokenVerifier =
            mock(LineOidcTokenVerifier.class);
    private final CloudTasksLineTaskRequestAuthenticator authenticator =
            new CloudTasksLineTaskRequestAuthenticator(
                    tokenVerifier,
                    properties()
            );

    @Test
    void acceptsVerifiedExpectedServiceAccount() {
        when(tokenVerifier.verify("valid-token"))
                .thenReturn(SERVICE_ACCOUNT);

        assertDoesNotThrow(() -> authenticator.authenticate(
                "Bearer valid-token"
        ));
        verify(tokenVerifier).verify("valid-token");
    }

    @Test
    void acceptsCaseInsensitiveBearerScheme() {
        when(tokenVerifier.verify("valid-token"))
                .thenReturn(SERVICE_ACCOUNT);

        assertDoesNotThrow(() -> authenticator.authenticate(
                "bearer valid-token"
        ));
    }

    @Test
    void rejectsMissingMalformedOrWrongServiceAccount() {
        assertInvalid(null);
        assertInvalid("Basic token");
        assertInvalid("Bearer ");

        when(tokenVerifier.verify("wrong-account"))
                .thenReturn("other@town-ai.iam.gserviceaccount.com");
        assertInvalid("Bearer wrong-account");
    }

    @Test
    void rejectsTokenVerificationFailure() {
        when(tokenVerifier.verify("invalid-token")).thenThrow(
                new LineOidcTokenVerificationException()
        );

        assertInvalid("Bearer invalid-token");
    }

    private void assertInvalid(String authorization) {
        ApiException exception = assertThrows(
                ApiException.class,
                () -> authenticator.authenticate(authorization)
        );
        assertEquals(
                ErrorCode.INVALID_LINE_TASK_AUTHORIZATION,
                exception.errorCode()
        );
    }

    private LineTaskProperties properties() {
        return new LineTaskProperties(
                "cloud-tasks",
                "",
                "town-ai",
                "asia-northeast1",
                "line-events",
                "https://town-ai.run.app/internal/tasks/line-events",
                "https://town-ai.run.app",
                SERVICE_ACCOUNT
        );
    }
}
