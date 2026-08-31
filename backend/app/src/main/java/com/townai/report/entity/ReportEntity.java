package com.townai.report.entity;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 생성된 Markdown Report의 메타데이터와 내부 Storage 경로를 저장한다.
 *
 * <p>본문은 DB에 중복 저장하지 않고 {@link #storagePath}가 가리키는 Storage 객체에
 * 보관한다. Report ID를 파일명에 사용하기 위해 메타데이터 Row를 먼저 생성하므로,
 * 생성 Transaction 중에는 Storage 경로가 일시적으로 {@code null}일 수 있다.
 * LINE에서 생성한 Report는 원본 Webhook Event ID를 선택적 UNIQUE 멱등 Key로
 * 보존한다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity {

    private Long id;

    private ReportType reportType;

    private String model;

    private String promptVersion;

    private String sourceFingerprint;

    /**
     * ID 선점 Transaction 안에서만 임시로 null일 수 있다.
     */
    private String storagePath;

    private String sourceWebhookEventId;

    private Instant createdAt;

    private Instant updatedAt;

    @Builder
    private ReportEntity(
            ReportType reportType,
            String model,
            String promptVersion,
            String sourceFingerprint,
            String sourceWebhookEventId
    ) {
        this.reportType = reportType;
        this.model = model;
        this.promptVersion = promptVersion;
        this.sourceFingerprint = sourceFingerprint;
        this.sourceWebhookEventId = sourceWebhookEventId;
    }

    public static ReportEntity restore(
            Long id,
            ReportType reportType,
            String model,
            String promptVersion,
            String sourceFingerprint,
            String storagePath,
            String sourceWebhookEventId,
            Instant createdAt,
            Instant updatedAt
    ) {
        // GCS 경로와 재사용 정보를 포함한 저장 당시 Metadata를 그대로 복원한다.
        ReportEntity report = ReportEntity.builder()
                .reportType(reportType)
                .model(model)
                .promptVersion(promptVersion)
                .sourceFingerprint(sourceFingerprint)
                .sourceWebhookEventId(sourceWebhookEventId)
                .build();
        report.id = id;
        report.storagePath = storagePath;
        report.createdAt = createdAt;
        report.updatedAt = updatedAt;
        return report;
    }

    /**
     * Repository 저장 결과의 ID와 Audit 시각을 Entity에 반영한다.
     *
     * @param persistedId 저장된 Report ID
     * @param persistedAt 저장이 완료된 UTC 시각
     */
    public void markPersisted(Long persistedId, Instant persistedAt) {
        // 최초 저장에서만 ID·생성 시각을 확정하고 재저장에서는 수정 시각만 갱신한다.
        if (id == null) {
            id = persistedId;
            createdAt = persistedAt;
        }
        updatedAt = persistedAt;
    }

    /**
     * Migration 이전에 생성된 Report에 현재 입력 지문을 연결한다.
     *
     * <p>생성 이후 원본 데이터가 변경되지 않았고 대상 Area가 동일한 경우에만
     * 호출한다. 새 Report는 Builder에서 지문을 바로 설정한다.</p>
     *
     * @param sourceFingerprint Prompt 입력과 버전으로 계산한 SHA-256 지문
     */
    public void assignSourceFingerprint(String sourceFingerprint) {
        this.sourceFingerprint = sourceFingerprint;
    }

    /**
     * 본문 저장이 성공한 뒤 해당 객체의 논리 경로를 연결한다.
     *
     * @param storagePath ReportStorage 구현체가 읽고 삭제할 내부 객체 경로
     */
    public void assignStoragePath(String storagePath) {
        this.storagePath = storagePath;
    }
}
