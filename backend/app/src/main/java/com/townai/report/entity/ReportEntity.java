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
 * 생성 Transaction 중에는 Storage 경로가 일시적으로 {@code null}일 수 있다.</p>
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

    /**
     * ID 선점 Transaction 안에서만 임시로 null일 수 있다.
     */
    @Column(name = "storage_path", length = 255)
    private String storagePath;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private ReportEntity(ReportType reportType, String model, String promptVersion) {
        this.reportType = reportType;
        this.model = model;
        this.promptVersion = promptVersion;
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
