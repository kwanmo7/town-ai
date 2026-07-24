package com.townai.line.security;

import com.townai.common.error.ApiException;

/**
 * 내부 LINE Task Endpoint 호출자가 현재 실행 환경에서 허용되는지 검증한다.
 */
public interface LineTaskRequestAuthenticator {

    /**
     * HTTP Authorization Header를 검증한다.
     *
     * @param authorizationHeader 요청의 Authorization Header
     * @throws ApiException Production OIDC 인증에 실패한 경우
     */
    void authenticate(String authorizationHeader);
}
