package com.townai.persistence.firestore;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Firestore Project, Database와 Local Emulator 연결 설정이다.
 *
 * @param projectId Database를 소유한 Google Cloud Project ID
 * @param databaseId Project 내부의 Firestore Database ID
 * @param emulatorHost {@code host:port} 형식의 선택적 Local Emulator 주소
 */
@ConfigurationProperties("town-ai.firestore")
public record FirestoreProperties(String projectId, String databaseId, String emulatorHost) {

    /** 입력값의 공백과 미설정 null을 정규화한다. */
    public FirestoreProperties {
        projectId = projectId == null ? "" : projectId.trim();
        databaseId = databaseId == null ? "" : databaseId.trim();
        emulatorHost = emulatorHost == null ? "" : emulatorHost.trim();
    }

    /**
     * Local Emulator 연결 설정 여부를 반환한다.
     *
     * @return Emulator 주소가 있으면 {@code true}
     */
    public boolean usesEmulator() {
        return !emulatorHost.isBlank();
    }
}
