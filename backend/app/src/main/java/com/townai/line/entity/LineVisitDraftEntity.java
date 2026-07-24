package com.townai.line.entity;

import com.townai.area.entity.AreaEntity;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.entity.VisitEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * LINE 자연어 입력의 검증된 Visit Draft와 사용자 확인 상태를 저장한다.
 *
 * <p>{@code sourceWebhookEventId}가 UNIQUE이므로 같은 Text Message 이벤트가
 * 재처리돼도 Draft를 하나만 유지한다. 확인 가능한 Draft는 Area, 방문일과 다섯
 * 점수가 모두 존재하며, 누락 값이 있으면 {@code NEEDS_INPUT}으로 저장한다.</p>
 */
@Entity
@Table(name = "line_visit_draft")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineVisitDraftEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "source_webhook_event_id",
            nullable = false,
            unique = true,
            length = 64
    )
    private String sourceWebhookEventId;

    @Column(name = "line_user_id", nullable = false, length = 64)
    private String lineUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "area_id")
    private AreaEntity area;

    @Column(name = "visit_date")
    private LocalDate visitDate;

    @Column(name = "atmosphere_score", columnDefinition = "TINYINT")
    private Integer atmosphereScore;

    @Column(name = "infra_score", columnDefinition = "TINYINT")
    private Integer infraScore;

    @Column(name = "clean_score", columnDefinition = "TINYINT")
    private Integer cleanScore;

    @Column(name = "size_score", columnDefinition = "TINYINT")
    private Integer sizeScore;

    @Column(name = "access_score", columnDefinition = "TINYINT")
    private Integer accessScore;

    @Column(columnDefinition = "TEXT")
    private String memo;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "JSON")
    private List<String> warnings;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LineVisitDraftStatus status;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "confirmed_visit_id", unique = true)
    private VisitEntity confirmedVisit;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private LineVisitDraftEntity(
            String sourceWebhookEventId,
            String lineUserId,
            AreaEntity area,
            LocalDate visitDate,
            Integer atmosphereScore,
            Integer infraScore,
            Integer cleanScore,
            Integer sizeScore,
            Integer accessScore,
            String memo,
            List<String> warnings,
            LineVisitDraftStatus status,
            Instant expiresAt
    ) {
        this.sourceWebhookEventId = sourceWebhookEventId;
        this.lineUserId = lineUserId;
        this.area = area;
        this.visitDate = visitDate;
        this.atmosphereScore = atmosphereScore;
        this.infraScore = infraScore;
        this.cleanScore = cleanScore;
        this.sizeScore = sizeScore;
        this.accessScore = accessScore;
        this.memo = memo;
        this.warnings = new ArrayList<>(warnings);
        this.status = status;
        this.expiresAt = expiresAt;
    }

    /**
     * Parser 응답을 24시간 유효한 LINE Draft로 변환한다.
     *
     * @param sourceWebhookEventId Draft를 생성한 Webhook Event ID
     * @param lineUserId Draft를 소유한 LINE User ID
     * @param area 현재도 활성 상태인 Parser 선택 Area
     * @param response Backend 검증을 통과한 Visit Parser 응답
     * @param warnings Area 재검증 결과까지 포함한 사용자 경고
     * @param createdAt Draft 유효 시간을 계산할 UTC 현재 시각
     * @return 저장 가능한 새 LINE Draft
     */
    public static LineVisitDraftEntity create(
            String sourceWebhookEventId,
            String lineUserId,
            AreaEntity area,
            VisitDraftResponse response,
            List<String> warnings,
            Instant createdAt
    ) {
        LineVisitDraftStatus status = isConfirmable(area, response)
                ? LineVisitDraftStatus.AWAITING_CONFIRMATION
                : LineVisitDraftStatus.NEEDS_INPUT;
        return LineVisitDraftEntity.builder()
                .sourceWebhookEventId(sourceWebhookEventId)
                .lineUserId(lineUserId)
                .area(area)
                .visitDate(response.visitDate())
                .atmosphereScore(response.atmosphereScore())
                .infraScore(response.infraScore())
                .cleanScore(response.cleanScore())
                .sizeScore(response.sizeScore())
                .accessScore(response.accessScore())
                .memo(response.memo())
                .warnings(warnings)
                .status(status)
                .expiresAt(createdAt.plus(24, ChronoUnit.HOURS)
                        .truncatedTo(ChronoUnit.SECONDS))
                .build();
    }

    /**
     * 확인 가능한 Draft를 생성된 Visit과 연결하고 확정 상태로 전환한다.
     *
     * @param visit 같은 Transaction에서 생성된 Visit
     */
    public void confirm(VisitEntity visit) {
        this.confirmedVisit = visit;
        this.status = LineVisitDraftStatus.CONFIRMED;
    }

    /**
     * 확인 대기 중인 Draft를 사용자 취소 상태로 전환한다.
     */
    public void cancel() {
        this.confirmedVisit = null;
        this.status = LineVisitDraftStatus.CANCELLED;
    }

    /**
     * 확인 기간이 지난 미확정 Draft를 만료 상태로 전환한다.
     */
    public void expire() {
        this.confirmedVisit = null;
        this.status = LineVisitDraftStatus.EXPIRED;
    }

    /**
     * 확인 직전 재검증에서 문제가 발견된 Draft를 재입력 상태로 전환한다.
     *
     * @param warning 사용자에게 안내할 재검증 실패 이유
     */
    public void requireNewInput(String warning) {
        this.confirmedVisit = null;
        this.status = LineVisitDraftStatus.NEEDS_INPUT;
        if (!this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }

    private static boolean isConfirmable(
            AreaEntity area,
            VisitDraftResponse response
    ) {
        return area != null
                && response.visitDate() != null
                && response.atmosphereScore() != null
                && response.infraScore() != null
                && response.cleanScore() != null
                && response.sizeScore() != null
                && response.accessScore() != null;
    }
}
