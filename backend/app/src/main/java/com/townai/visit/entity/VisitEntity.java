package com.townai.visit.entity;

import com.townai.area.entity.AreaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SourceType;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Area를 직접 방문한 날짜와 다섯 가지 주관 평가를 저장하는 JPA Entity이다.
 *
 * <p>Visit은 사용자가 입력한 원본 평가의 Source of Truth이다. Area가 논리 삭제돼도
 * Visit Row와 연관 관계는 보존한다. 생성에는 Builder를 사용하고, 수정은 영속 상태
 * Entity의 {@link #update} 메서드로만 처리한다.</p>
 */
@Entity
@Table(name = "visit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 여러 Visit이 하나의 Area를 참조한다. Visit 변경이 Area로 전파되지 않도록 Cascade는 사용하지 않는다.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "area_id", nullable = false)
    private AreaEntity area;

    @Column(name = "visit_date", nullable = false)
    private LocalDate visitDate;

    @Min(0)
    @Max(10)
    @Column(name = "atmosphere_score", nullable = false, columnDefinition = "TINYINT")
    private int atmosphereScore;

    @Min(0)
    @Max(10)
    @Column(name = "infra_score", nullable = false, columnDefinition = "TINYINT")
    private int infraScore;

    @Min(0)
    @Max(10)
    @Column(name = "clean_score", nullable = false, columnDefinition = "TINYINT")
    private int cleanScore;

    @Min(0)
    @Max(10)
    @Column(name = "size_score", nullable = false, columnDefinition = "TINYINT")
    private int sizeScore;

    @Min(0)
    @Max(10)
    @Column(name = "access_score", nullable = false, columnDefinition = "TINYINT")
    private int accessScore;

    @Column(name = "memo", columnDefinition = "TEXT")
    private String memo;

    @CreationTimestamp(source = SourceType.DB)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp(source = SourceType.DB)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private VisitEntity(
            AreaEntity area,
            LocalDate visitDate,
            int atmosphereScore,
            int infraScore,
            int cleanScore,
            int sizeScore,
            int accessScore,
            String memo
    ) {
        this.area = area;
        this.visitDate = visitDate;
        this.atmosphereScore = atmosphereScore;
        this.infraScore = infraScore;
        this.cleanScore = cleanScore;
        this.sizeScore = sizeScore;
        this.accessScore = accessScore;
        this.memo = memo;
    }

    /**
     * PUT 요청으로 전달된 Visit 전체 값을 교체한다.
     *
     * @param area 변경할 활성 Area
     * @param visitDate 변경할 방문일
     * @param atmosphereScore 분위기 점수
     * @param infraScore 생활 인프라 점수
     * @param cleanScore 청결도 점수
     * @param sizeScore 넓은 집 가능성 점수
     * @param accessScore 접근성 점수
     * @param memo 정규화된 방문 메모
     */
    public void update(
            AreaEntity area,
            LocalDate visitDate,
            int atmosphereScore,
            int infraScore,
            int cleanScore,
            int sizeScore,
            int accessScore,
            String memo
    ) {
        this.area = area;
        this.visitDate = visitDate;
        this.atmosphereScore = atmosphereScore;
        this.infraScore = infraScore;
        this.cleanScore = cleanScore;
        this.sizeScore = sizeScore;
        this.accessScore = accessScore;
        this.memo = memo;
    }
}
