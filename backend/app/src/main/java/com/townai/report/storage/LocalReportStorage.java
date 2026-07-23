package com.townai.report.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 로컬 개발 환경에서 Markdown Report를 UTF-8 파일로 저장하는 구현체이다.
 *
 * <p>DB의 논리 경로에서 공통 {@code reports/} Prefix를 제거한 뒤 설정된 Root 아래로
 * 변환한다. 정규화한 결과가 Root를 벗어나면 Path Traversal 시도로 보고 거부한다.
 * GCS 구현과 동일하게 없는 객체 삭제는 성공으로 처리한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.report-storage",
        name = "type",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalReportStorage implements ReportStorage {

    private static final String REPORTS_PREFIX = "reports/";

    private final Path rootDirectory;

    /**
     * 로컬 Report 저장소의 Root 경로를 확정한다.
     *
     * @param localDirectory Report 파일을 저장할 기준 디렉터리
     */
    public LocalReportStorage(
            @Value("${town-ai.report-storage.local-directory:./data/reports}")
            String localDirectory
    ) {
        this.rootDirectory = Path.of(localDirectory).toAbsolutePath().normalize();
    }

    @Override
    public void write(String storagePath, String content) {
        Path target = resolve(storagePath);
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(target, content, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ReportStorageException("Failed to write Report file.", exception);
        }
    }

    @Override
    public String read(String storagePath) {
        try {
            return Files.readString(resolve(storagePath), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new ReportStorageException("Failed to read Report file.", exception);
        }
    }

    @Override
    public void delete(String storagePath) {
        try {
            Files.deleteIfExists(resolve(storagePath));
        } catch (IOException exception) {
            throw new ReportStorageException("Failed to delete Report file.", exception);
        }
    }

    /**
     * 논리 Storage 경로를 안전한 로컬 절대 경로로 변환한다.
     */
    private Path resolve(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            throw new ReportStorageException(
                    "Report storage path is empty.",
                    new IllegalArgumentException("storagePath")
            );
        }

        String relativePath = storagePath.replace('\\', '/');
        if (relativePath.startsWith(REPORTS_PREFIX)) {
            relativePath = relativePath.substring(REPORTS_PREFIX.length());
        }

        Path target = rootDirectory.resolve(relativePath).normalize();
        if (!target.startsWith(rootDirectory)) {
            throw new ReportStorageException(
                    "Report storage path is outside the configured directory.",
                    new IllegalArgumentException("storagePath")
            );
        }
        return target;
    }
}
