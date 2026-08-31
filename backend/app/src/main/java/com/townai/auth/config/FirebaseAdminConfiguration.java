package com.townai.auth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * Cloud Run의 Application Default Credentials로 Firebase Admin SDK를 초기화한다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "town-ai.web-auth", name = "enabled", havingValue = "true")
public class FirebaseAdminConfiguration {

    /**
     * 인증이 활성화된 환경에서 사용할 Firebase App을 생성한다.
     *
     * @param properties Firebase Project 설정
     * @return Application Default Credentials를 사용하는 Firebase App
     * @throws IOException Application Default Credentials를 읽지 못한 경우
     */
    @Bean(destroyMethod = "delete")
    FirebaseApp firebaseApp(WebAuthProperties properties) throws IOException {
        requireConfigured(properties);
        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.getApplicationDefault())
                .setProjectId(properties.firebaseProjectId())
                .build();
        return FirebaseApp.initializeApp(options);
    }

    /**
     * Firebase ID Token 검증기를 제공한다.
     *
     * @param firebaseApp 초기화된 Firebase App
     * @return Firebase Authentication Client
     */
    @Bean
    FirebaseAuth firebaseAuth(FirebaseApp firebaseApp) {
        return FirebaseAuth.getInstance(firebaseApp);
    }

    private void requireConfigured(WebAuthProperties properties) {
        if (properties.firebaseProjectId().isBlank()) {
            throw new IllegalStateException(
                    "FIREBASE_PROJECT_ID is required when Web authentication is enabled."
            );
        }
        if (properties.allowedUid().isBlank()) {
            throw new IllegalStateException(
                    "FIREBASE_ALLOWED_UID is required when Web authentication is enabled."
            );
        }
    }
}
