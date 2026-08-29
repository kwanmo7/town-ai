package com.townai.persistence.firestore;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Configures the server-side Firestore client for Cloud Run and local emulation. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FirestoreProperties.class)
public class FirestoreConfiguration {

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
