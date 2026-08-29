package com.townai.line.entity;

import com.townai.area.entity.AreaEntity;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.entity.VisitEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * LINE 자연어 입력의 검증된 Visit Draft와 사용자 확인 상태를 저장한다.
 *
 * <p>{@code sourceWebhookEventId}가 UNIQUE이므로 같은 Text Message 이벤트가
 * 재처리돼도 Draft를 하나만 유지한다. 확인 가능한 Draft는 기존 Area 또는 등록에
 * 필요한 신규 Area 위치, 방문일과 다섯 점수가 모두 존재하며, 누락 값이 있으면
 * {@code NEEDS_INPUT}으로 저장한다.</p>
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LineVisitDraftEntity {

    private Long id;

    private String sourceWebhookEventId;

    private String lineUserId;

    private AreaEntity area;

    private boolean areaRegistrationRequired;

    private String areaName;

    private String areaPrefecture;

    private String areaCity;

    private String areaStation;

    private LocalDate visitDate;

    private Integer atmosphereScore;

    private Integer infraScore;

    private Integer cleanScore;

    private Integer sizeScore;

    private Integer accessScore;

    private String memo;

    private List<String> warnings;

    private LineVisitDraftStatus status;

    private Instant expiresAt;

    private String revisionWebhookEventId;

    private VisitEntity confirmedVisit;

    private Instant createdAt;

    private Instant updatedAt;

    @Builder(access = AccessLevel.PRIVATE)
    private LineVisitDraftEntity(
            String sourceWebhookEventId,
            String lineUserId,
            AreaEntity area,
            boolean areaRegistrationRequired,
            String areaName,
            String areaPrefecture,
            String areaCity,
            String areaStation,
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
        this.areaRegistrationRequired = areaRegistrationRequired;
        this.areaName = areaName;
        this.areaPrefecture = areaPrefecture;
        this.areaCity = areaCity;
        this.areaStation = areaStation;
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

    public static LineVisitDraftEntity restore(
            Long id,
            String sourceWebhookEventId,
            String lineUserId,
            AreaEntity area,
            boolean areaRegistrationRequired,
            String areaName,
            String areaPrefecture,
            String areaCity,
            String areaStation,
            LocalDate visitDate,
            Integer atmosphereScore,
            Integer infraScore,
            Integer cleanScore,
            Integer sizeScore,
            Integer accessScore,
            String memo,
            List<String> warnings,
            LineVisitDraftStatus status,
            Instant expiresAt,
            String revisionWebhookEventId,
            VisitEntity confirmedVisit,
            Instant createdAt,
            Instant updatedAt
    ) {
        LineVisitDraftEntity draft = LineVisitDraftEntity.builder()
                .sourceWebhookEventId(sourceWebhookEventId)
                .lineUserId(lineUserId)
                .area(area)
                .areaRegistrationRequired(areaRegistrationRequired)
                .areaName(areaName)
                .areaPrefecture(areaPrefecture)
                .areaCity(areaCity)
                .areaStation(areaStation)
                .visitDate(visitDate)
                .atmosphereScore(atmosphereScore)
                .infraScore(infraScore)
                .cleanScore(cleanScore)
                .sizeScore(sizeScore)
                .accessScore(accessScore)
                .memo(memo)
                .warnings(warnings == null ? List.of() : warnings)
                .status(status)
                .expiresAt(expiresAt)
                .build();
        draft.id = id;
        draft.revisionWebhookEventId = revisionWebhookEventId;
        draft.confirmedVisit = confirmedVisit;
        draft.createdAt = createdAt;
        draft.updatedAt = updatedAt;
        return draft;
    }

    public void markPersisted(Long persistedId, Instant persistedAt) {
        if (id == null) {
            id = persistedId;
            createdAt = persistedAt;
        }
        updatedAt = persistedAt;
    }

    /**
     * Parser 응답을 24시간 유효한 LINE Draft로 변환한다.
     *
     * @param sourceWebhookEventId Draft를 생성한 Webhook Event ID
     * @param lineUserId Draft를 소유한 LINE User ID
     * @param area 현재도 활성 상태인 Parser 선택 Area
     * @param areaRegistrationRequired 저장 시 새 Area를 함께 등록해야 하는지 여부
     * @param response Backend 검증을 통과한 Visit Parser 응답
     * @param warnings Area 재검증 결과까지 포함한 사용자 경고
     * @param createdAt Draft 유효 시간을 계산할 UTC 현재 시각
     * @return 저장 가능한 새 LINE Draft
     */
    public static LineVisitDraftEntity create(
            String sourceWebhookEventId,
            String lineUserId,
            AreaEntity area,
            boolean areaRegistrationRequired,
            VisitDraftResponse response,
            List<String> warnings,
            Instant createdAt
    ) {
        LineVisitDraftStatus status = isConfirmable(
                area,
                areaRegistrationRequired,
                response
        )
                ? LineVisitDraftStatus.AWAITING_CONFIRMATION
                : LineVisitDraftStatus.NEEDS_INPUT;
        VisitDraftAreaResponse areaResponse = response.area();
        return LineVisitDraftEntity.builder()
                .sourceWebhookEventId(sourceWebhookEventId)
                .lineUserId(lineUserId)
                .area(area)
                .areaRegistrationRequired(areaRegistrationRequired)
                .areaName(areaResponse == null ? null : areaResponse.name())
                .areaPrefecture(
                        areaResponse == null ? null : areaResponse.prefecture()
                )
                .areaCity(areaResponse == null ? null : areaResponse.city())
                .areaStation(
                        areaResponse == null ? null : areaResponse.station()
                )
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
     * 확인 과정에서 찾거나 새로 만든 활성 Area를 Draft에 연결한다.
     *
     * @param area Visit이 참조할 활성 Area
     */
    public void assignArea(AreaEntity area) {
        this.area = area;
        this.areaRegistrationRequired = false;
    }

    /**
     * 확인 가능한 Draft를 생성된 Visit과 연결하고 확정 상태로 전환한다.
     *
     * @param visit 같은 Firestore Transaction에서 생성된 Visit Reference
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
        this.revisionWebhookEventId = null;
        this.status = LineVisitDraftStatus.CANCELLED;
    }

    /**
     * 다음 LINE Text Message로 현재 값을 수정할 수 있는 대기 상태로 전환한다.
     */
    public void requestRevision() {
        this.confirmedVisit = null;
        this.revisionWebhookEventId = null;
        this.status = LineVisitDraftStatus.AWAITING_REVISION;
    }

    /**
     * 수정 대기 Draft를 하나의 Text Message가 처리하도록 점유한다.
     *
     * @param webhookEventId 수정 내용을 담은 LINE Webhook Event ID
     */
    public void beginRevision(String webhookEventId) {
        if (status != LineVisitDraftStatus.AWAITING_REVISION) {
            throw new IllegalStateException(
                    "Only an awaiting Draft can begin revision."
            );
        }
        this.revisionWebhookEventId = webhookEventId;
        this.status = LineVisitDraftStatus.REVISION_PROCESSING;
    }

    /**
     * 현재 처리 중인 Text Message가 이 Draft를 점유했는지 확인한다.
     *
     * @param webhookEventId 확인할 LINE Webhook Event ID
     * @return 같은 수정 처리이면 {@code true}
     */
    public boolean isRevisionClaimedBy(String webhookEventId) {
        return status == LineVisitDraftStatus.REVISION_PROCESSING
                && revisionWebhookEventId != null
                && revisionWebhookEventId.equals(webhookEventId);
    }

    /**
     * 수정된 새 Draft가 생성된 뒤 현재 Draft를 대체 완료 상태로 전환한다.
     */
    public void supersede() {
        this.confirmedVisit = null;
        this.revisionWebhookEventId = null;
        this.status = LineVisitDraftStatus.SUPERSEDED;
    }

    /**
     * Parser 수정 모드에 전달할 현재 Draft Snapshot을 만든다.
     *
     * @return 현재 화면에 표시된 Area와 Visit 값
     */
    public VisitDraftResponse toDraftResponse() {
        VisitDraftAreaResponse areaResponse = area == null
                ? candidateAreaResponse()
                : new VisitDraftAreaResponse(
                        area.getId(),
                        area.getName(),
                        area.getPrefecture(),
                        area.getCity(),
                        area.getStation()
                );
        return new VisitDraftResponse(
                areaResponse,
                visitDate,
                atmosphereScore,
                infraScore,
                cleanScore,
                sizeScore,
                accessScore,
                memo,
                List.copyOf(warnings)
        );
    }

    /**
     * 확인 기간이 지난 미확정 Draft를 만료 상태로 전환한다.
     */
    public void expire() {
        this.confirmedVisit = null;
        this.revisionWebhookEventId = null;
        this.status = LineVisitDraftStatus.EXPIRED;
    }

    /**
     * 확인 직전 재검증에서 문제가 발견된 Draft를 재입력 상태로 전환한다.
     *
     * @param warning 사용자에게 안내할 재검증 실패 이유
     */
    public void requireNewInput(String warning) {
        this.confirmedVisit = null;
        this.revisionWebhookEventId = null;
        this.status = LineVisitDraftStatus.NEEDS_INPUT;
        if (!this.warnings.contains(warning)) {
            this.warnings.add(warning);
        }
    }

    private VisitDraftAreaResponse candidateAreaResponse() {
        if (areaName == null || areaName.isBlank()) {
            return null;
        }
        return new VisitDraftAreaResponse(
                null,
                areaName,
                areaPrefecture,
                areaCity,
                areaStation
        );
    }

    private static boolean isConfirmable(
            AreaEntity area,
            boolean areaRegistrationRequired,
            VisitDraftResponse response
    ) {
        boolean areaReady = area != null
                || (areaRegistrationRequired
                && response.area() != null
                && response.area().hasRequiredLocation());
        return areaReady
                && response.visitDate() != null
                && response.atmosphereScore() != null
                && response.infraScore() != null
                && response.cleanScore() != null
                && response.sizeScore() != null
                && response.accessScore() != null;
    }
}
