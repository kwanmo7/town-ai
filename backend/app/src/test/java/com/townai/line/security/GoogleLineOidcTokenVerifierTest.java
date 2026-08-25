package com.townai.line.security;

import com.townai.line.config.LineTaskProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class GoogleLineOidcTokenVerifierTest {

    @Test
    void rejectsMalformedTokenAsVerificationFailure() {
        GoogleLineOidcTokenVerifier verifier =
                new GoogleLineOidcTokenVerifier(properties());

        assertThrows(
                LineOidcTokenVerificationException.class,
                () -> verifier.verify("invalid-token")
        );
    }

    @Test
    void rejectsStructurallyInvalidJwtAsVerificationFailure() {
        GoogleLineOidcTokenVerifier verifier =
                new GoogleLineOidcTokenVerifier(properties());

        assertThrows(
                LineOidcTokenVerificationException.class,
                () -> verifier.verify("a.b.c")
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
                "line-task@town-ai.iam.gserviceaccount.com"
        );
    }
}
