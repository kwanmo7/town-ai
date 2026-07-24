package com.townai.report.storage;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * Production에서 Markdown Report를 Google Cloud Storage 객체로 관리한다.
 *
 * <p>애플리케이션은 Bucket을 생성하거나 공개하지 않는다. DB의 논리 경로를
 * 비공개 Bucket 내부 객체 이름으로 그대로 사용하고 UTF-8 Markdown Content-Type을
 * 저장한다. 없는 객체 삭제는 {@link ReportStorage} 계약에 따라 성공이다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.report-storage",
        name = "type",
        havingValue = "gcs"
)
public class GcsReportStorage implements ReportStorage {

    private static final String MARKDOWN_CONTENT_TYPE =
            "text/markdown; charset=UTF-8";

    private final Storage storage;
    private final String bucketName;

    /**
     * GCS Report Storage를 생성하고 필수 Bucket 이름을 검증한다.
     *
     * @param storage ADC를 사용하는 Google Cloud Storage Client
     * @param bucketName 배포 단계에서 만든 비공개 Report Bucket 이름
     */
    public GcsReportStorage(
            Storage storage,
            @Value("${town-ai.report-storage.bucket-name:}")
            String bucketName
    ) {
        if (bucketName == null || bucketName.isBlank()) {
            throw new IllegalStateException(
                    "GCS bucket name must be configured."
            );
        }
        this.storage = storage;
        this.bucketName = bucketName.trim();
    }

    @Override
    public void write(String storagePath, String content) {
        String objectName = ReportStoragePathValidator.validate(storagePath);
        ReportStoragePathValidator.requireContent(content);
        BlobInfo blobInfo = BlobInfo.newBuilder(
                        BlobId.of(bucketName, objectName)
                )
                .setContentType(MARKDOWN_CONTENT_TYPE)
                .build();
        try {
            storage.create(
                    blobInfo,
                    content.getBytes(StandardCharsets.UTF_8)
            );
        } catch (StorageException exception) {
            throw new ReportStorageException(
                    "Failed to write Report object.",
                    exception
            );
        }
    }

    @Override
    public String read(String storagePath) {
        BlobId blobId = BlobId.of(
                bucketName,
                ReportStoragePathValidator.validate(storagePath)
        );
        try {
            byte[] content = storage.readAllBytes(blobId);
            return new String(content, StandardCharsets.UTF_8);
        } catch (StorageException exception) {
            throw new ReportStorageException(
                    "Failed to read Report object.",
                    exception
            );
        }
    }

    @Override
    public void delete(String storagePath) {
        BlobId blobId = BlobId.of(
                bucketName,
                ReportStoragePathValidator.validate(storagePath)
        );
        try {
            storage.delete(blobId);
        } catch (StorageException exception) {
            throw new ReportStorageException(
                    "Failed to delete Report object.",
                    exception
            );
        }
    }

}
