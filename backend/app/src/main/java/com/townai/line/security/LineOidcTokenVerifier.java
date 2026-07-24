package com.townai.line.security;

/**
 * Google이 서명한 OIDC ID Token을 검증하고 호출 Service Account를 식별한다.
 */
public interface LineOidcTokenVerifier {

    /**
     * Token의 서명, Issuer, Audience와 만료를 검증한다.
     *
     * @param token Bearer 접두어를 제거한 OIDC ID Token
     * @return 검증된 Token의 Service Account Email
     * @throws LineOidcTokenVerificationException Token을 신뢰할 수 없는 경우
     */
    String verify(String token);
}
