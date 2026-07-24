package com.townai.line.security;

/**
 * Google OIDC Token의 서명 또는 Claim 검증에 실패한 내부 예외이다.
 */
public class LineOidcTokenVerificationException
        extends RuntimeException {

    /**
     * OIDC Token 검증 실패를 생성한다.
     *
     * @param cause Google 인증 Library가 반환한 실패 원인
     */
    public LineOidcTokenVerificationException(Throwable cause) {
        super("LINE Task OIDC token verification failed.", cause);
    }

    /**
     * 필수 Claim이 없거나 예상 형식이 아닌 검증 실패를 생성한다.
     */
    public LineOidcTokenVerificationException() {
        super("LINE Task OIDC token claims are invalid.");
    }
}
