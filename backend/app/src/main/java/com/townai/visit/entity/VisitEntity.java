package com.townai.visit.entity;

import com.townai.area.entity.AreaEntity;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Area를 직접 방문한 날짜와 다섯 가지 주관 평가를 표현하는 Domain Entity이다.
 *
 * <p>Visit은 사용자가 입력한 원본 평가의 Source of Truth이다. Area가 논리 삭제돼도
 * Visit Row와 연관 관계는 보존한다. 생성에는 Builder를 사용하고, 수정은 영속 상태
 * Entity의 {@link #update} 메서드로만 처리한다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class VisitEntity {

    private Long id;

    /**
     * 여러 Visit이 하나의 Area를 참조한다. Visit 변경이 Area로 전파되지 않도록 Cascade는 사용하지 않는다.
     */
    private AreaEntity area;

    private LocalDate visitDate;

    @Min(0)
    @Max(10)
    private int atmosphereScore;

    @Min(0)
    @Max(10)
    private int infraScore;

    @Min(0)
    @Max(10)
    private int cleanScore;

    @Min(0)
    @Max(10)
    private int sizeScore;

    @Min(0)
    @Max(10)
    private int accessScore;

    private String memo;

    private Instant createdAt;

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

    public static VisitEntity restore(
            Long id,
            AreaEntity area,
            LocalDate visitDate,
            int atmosphereScore,
            int infraScore,
            int cleanScore,
            int sizeScore,
            int accessScore,
            String memo,
            Instant createdAt,
            Instant updatedAt
    ) {
        // Firestore 문서의 ID와 Audit 시각까지 포함해 Domain Entity를 재구성한다.
        VisitEntity visit = VisitEntity.builder()
                .area(area)
                .visitDate(visitDate)
                .atmosphereScore(atmosphereScore)
                .infraScore(infraScore)
                .cleanScore(cleanScore)
                .sizeScore(sizeScore)
                .accessScore(accessScore)
                .memo(memo)
                .build();
        visit.id = id;
        visit.createdAt = createdAt;
        visit.updatedAt = updatedAt;
        return visit;
    }

    /**
     * 다른 문서가 새로 저장된 Visit ID만 참조할 때 사용하는 경량 Reference이다.
     * Firestore Transaction에서 쓰기 이후 문서를 다시 읽지 않도록 한다.
     *
     * @param id 참조할 Visit ID
     * @return ID만 설정된 Visit Reference
     */
    public static VisitEntity reference(Long id) {
        VisitEntity visit = new VisitEntity();
        visit.id = id;
        return visit;
    }

    /**
     * Repository 저장 결과의 ID와 Audit 시각을 Entity에 반영한다.
     *
     * @param persistedId 저장된 Visit ID
     * @param persistedAt 저장이 완료된 UTC 시각
     */
    public void markPersisted(Long persistedId, Instant persistedAt) {
        // 최초 저장과 수정에서 Audit 필드를 같은 규칙으로 반영한다.
        if (id == null) {
            id = persistedId;
            createdAt = persistedAt;
        }
        updatedAt = persistedAt;
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
