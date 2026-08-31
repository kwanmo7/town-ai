package com.townai.persistence.firestore;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cloud Run과 Local Emulator에서 사용할 Server-side Firestore Client를 구성한다. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfiguration {

    /**
     * Production ADC 또는 Local Emulator Credential을 사용하는 Firestore Client를 만든다.
     *
     * @param properties Firestore 연결 설정
     * @return 애플리케이션에서 공유할 Firestore Client
     */
    @Bean(destroyMethod = "close")
    Firestore firestore(FirestoreProperties properties) {
        if (properties.projectId().isBlank()) {
            throw new IllegalStateException("FIRESTORE_PROJECT_ID is required.");
        }
        if (properties.databaseId().isBlank()) {
            throw new IllegalStateException("FIRESTORE_DATABASE_ID is required.");
        }

        FirestoreOptions.Builder builder = FirestoreOptions.newBuilder()
                .setProjectId(properties.projectId())
                .setDatabaseId(properties.databaseId());
        if (properties.usesEmulator()) {
            builder
                    .setEmulatorHost(properties.emulatorHost())
                    .setCredentials(
                            new FirestoreOptions.EmulatorCredentials()
                    );
        }
        return builder.build().getService();
    }
}
