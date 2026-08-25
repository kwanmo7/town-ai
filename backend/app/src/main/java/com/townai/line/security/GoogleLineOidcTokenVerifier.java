package com.townai.line.security;

import com.google.api.client.json.webtoken.JsonWebSignature;
import com.google.auth.oauth2.TokenVerifier;
import com.townai.line.config.LineTaskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Google 공개 인증서로 Cloud Tasks OIDC ID Token을 검증한다.
 *
 * <p>{@link TokenVerifier}가 서명, 만료, Issuer와 Audience를 검증하며, 이 구현은
 * 추가로 검증된 Service Account Email Claim을 반환한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.line",
        name = "event-dispatcher",
        havingValue = "cloud-tasks"
)
public class GoogleLineOidcTokenVerifier
        implements LineOidcTokenVerifier {

    private static final String GOOGLE_ISSUER =
            "https://accounts.google.com";

    private final TokenVerifier tokenVerifier;

    /**
     * 설정된 Cloud Run Audience를 사용하는 Token 검증기를 생성한다.
     *
     * @param properties Cloud Tasks OIDC Audience 설정
     */
    public GoogleLineOidcTokenVerifier(LineTaskProperties properties) {
        String audience = requireNonBlank(
                properties.cloudTasksOidcAudience(),
                "Cloud Tasks OIDC audience"
        );
        this.tokenVerifier = TokenVerifier.newBuilder()
                .setAudience(audience)
                .setIssuer(GOOGLE_ISSUER)
                .build();
    }

    /**
     * Google 서명과 필수 Claim을 검증하고 Email을 반환한다.
     *
     * @param token Bearer 접두어를 제거한 OIDC ID Token
     * @return 검증된 Service Account Email
     */
    @Override
    public String verify(String token) {
        try {
            JsonWebSignature verified = tokenVerifier.verify(token);
            Object emailClaim = verified.getPayload().get("email");
            Object verifiedClaim =
                    verified.getPayload().get("email_verified");
            if (!(emailClaim instanceof String email)
                    || email.isBlank()
                    || !Boolean.TRUE.equals(verifiedClaim)) {
                throw new LineOidcTokenVerificationException();
            }
            return email;
        } catch (TokenVerifier.VerificationException
                 | IllegalArgumentException exception) {
            throw new LineOidcTokenVerificationException(exception);
        }
    }

    private String requireNonBlank(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " is required.");
        }
        return value;
    }
}
