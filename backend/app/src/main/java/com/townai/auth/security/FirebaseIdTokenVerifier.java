package com.townai.auth.security;

/**
 * Firebase Client SDK가 발급한 ID Token의 서명과 발급자를 검증한다.
 */
public interface FirebaseIdTokenVerifier {

    /**
     * ID Token을 검증하고 인증된 사용자 정보를 반환한다.
     *
     * @param idToken Bearer Token에서 추출한 Firebase ID Token
     * @return 인증된 Firebase 사용자
     * @throws InvalidFirebaseTokenException Token이 유효하지 않은 경우
     */
    AuthenticatedWebUser verify(String idToken);
}
