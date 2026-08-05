package com.townai.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

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
@Entity
@Table(name = "report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "report_type", nullable = false, length = 10)
    private ReportType reportType;

    @Column(nullable = false, length = 50)
    private String model;

    @Column(name = "prompt_version", nullable = false, length = 30)
    private String promptVersion;

    @Column(name = "source_fingerprint", length = 64)
    private String sourceFingerprint;

    /**
     * ID 선점 Transaction 안에서만 임시로 null일 수 있다.
     */
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @Column(name = "source_webhook_event_id", unique = true, length = 64)
    private String sourceWebhookEventId;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
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
