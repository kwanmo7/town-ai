package com.townai.line.security;

import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.line.config.LineTaskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Production 내부 Task 요청의 Google OIDC Token과 Service Account를 검증한다.
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.line",
        name = "event-dispatcher",
        havingValue = "cloud-tasks"
)
public class CloudTasksLineTaskRequestAuthenticator
        implements LineTaskRequestAuthenticator {

    private static final String BEARER_PREFIX = "Bearer ";

    private final LineOidcTokenVerifier tokenVerifier;
    private final String expectedServiceAccount;

    /**
     * Production Task 요청 인증기를 생성한다.
     *
     * @param tokenVerifier Google OIDC 서명 및 Claim 검증기
     * @param properties 허용할 Cloud Tasks Service Account 설정
     */
    public CloudTasksLineTaskRequestAuthenticator(
            LineOidcTokenVerifier tokenVerifier,
            LineTaskProperties properties
    ) {
        this.tokenVerifier = tokenVerifier;
        this.expectedServiceAccount = requireNonBlank(
                properties.cloudTasksServiceAccount(),
                "Cloud Tasks service account"
        );
    }

    /**
     * Bearer Token과 검증된 Service Account Email이 설정과 일치하는지 확인한다.
     *
     * @param authorizationHeader Cloud Tasks가 생성한 Authorization Header
     */
    @Override
    public void authenticate(String authorizationHeader) {
        if (authorizationHeader == null
                || !authorizationHeader.regionMatches(
                        true,
                        0,
                        BEARER_PREFIX,
                        0,
                        BEARER_PREFIX.length()
                )
                || authorizationHeader.length() == BEARER_PREFIX.length()) {
            throw invalidAuthorization();
        }

        try {
            String email = tokenVerifier.verify(
                    authorizationHeader.substring(BEARER_PREFIX.length())
            );
            if (!expectedServiceAccount.equals(email)) {
                throw invalidAuthorization();
            }
        } catch (LineOidcTokenVerificationException exception) {
            throw invalidAuthorization();
        }
    }

    private ApiException invalidAuthorization() {
        return new ApiException(
                ErrorCode.INVALID_LINE_TASK_AUTHORIZATION
        );
    }

    private String requireNonBlank(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " is required.");
        }
        return value;
    }
}
