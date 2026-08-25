package com.townai.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Web 관리 API의 Firebase 인증 설정이다.
 *
 * @param enabled Web 관리 API 인증 활성화 여부
 * @param firebaseProjectId ID Token 발급자를 검증할 Firebase Project ID
 * @param allowedUid Town AI 관리 화면 접근을 허용할 단일 Firebase UID
 */
@ConfigurationProperties(prefix = "town-ai.web-auth")
public record WebAuthProperties(
        boolean enabled,
        String firebaseProjectId,
        String allowedUid
) {

    /**
     * 문자열 설정의 앞뒤 공백을 제거한다.
     */
    public WebAuthProperties {
        firebaseProjectId = normalize(firebaseProjectId);
        allowedUid = normalize(allowedUid);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
