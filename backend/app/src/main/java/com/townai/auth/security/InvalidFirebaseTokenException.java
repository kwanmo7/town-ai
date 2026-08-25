package com.townai.auth.security;

/**
 * Firebase ID Token의 형식, 서명, 발급자 또는 만료 검증 실패를 나타낸다.
 */
public class InvalidFirebaseTokenException extends RuntimeException {

    /**
     * 내부 Firebase 예외를 보존하며 인증 실패 예외를 생성한다.
     *
     * @param cause Firebase Admin SDK가 반환한 검증 실패 원인
     */
    public InvalidFirebaseTokenException(Throwable cause) {
        super("Firebase ID token verification failed.", cause);
    }
}
