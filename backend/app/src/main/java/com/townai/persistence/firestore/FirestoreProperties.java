package com.townai.persistence.firestore;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore project and local emulator connection settings.
 *
 * @param projectId Google Cloud project that owns the database
 * @param databaseId Firestore database ID within the project
 * @param emulatorHost optional local emulator host in {@code host:port} form
 */
@ConfigurationProperties("town-ai.firestore")
public record FirestoreProperties(String projectId, String databaseId, String emulatorHost) {

    public FirestoreProperties {
        projectId = projectId == null ? "" : projectId.trim();
        databaseId = databaseId == null ? "" : databaseId.trim();
        emulatorHost = emulatorHost == null ? "" : emulatorHost.trim();
    }

    public boolean usesEmulator() {
        return !emulatorHost.isBlank();
    }
}
