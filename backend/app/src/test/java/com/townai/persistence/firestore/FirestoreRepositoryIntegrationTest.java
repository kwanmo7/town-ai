package com.townai.persistence.firestore;

import com.google.cloud.firestore.Firestore;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.FirestoreAreaRepository;
import com.townai.area.service.impl.AreaServiceImpl;
import com.townai.line.entity.LineVisitDraftEntity;
import com.townai.line.entity.LineWebhookEventEntity;
import com.townai.line.model.LineWebhookEventPayload;
import com.townai.line.model.LineWebhookEventType;
import com.townai.line.model.LineDraftAction;
import com.townai.line.model.LineDraftCommand;
import com.townai.line.repository.FirestoreLineVisitDraftRepository;
import com.townai.line.repository.FirestoreLineWebhookEventRepository;
import com.townai.line.service.LineVisitDraftActionService;
import com.townai.report.entity.ReportAreaEntity;
import com.townai.report.entity.ReportEntity;
import com.townai.report.entity.ReportType;
import com.townai.report.repository.FirestoreReportAreaRepository;
import com.townai.report.repository.FirestoreReportRepository;
import com.townai.visit.dto.VisitDraftAreaResponse;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.FirestoreVisitRepository;
import com.townai.visit.service.impl.VisitServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(
        named = "FIRESTORE_EMULATOR_HOST",
        matches = ".+"
)
class FirestoreRepositoryIntegrationTest {

    private static final Instant NOW =
            Instant.parse("2026-08-30T04:00:00Z");

    @Test
    void persistsTownAiAggregateWorkflowAgainstEmulator() throws Exception {
        String emulatorHost = System.getenv("FIRESTORE_EMULATOR_HOST");
        String projectId = "demo-town-ai-integration-" + System.nanoTime();
        Firestore firestore = new FirestoreConfiguration().firestore(
                new FirestoreProperties(projectId, "town-ai", emulatorHost)
        );

        try {
            assertEquals("town-ai", firestore.getOptions().getDatabaseId());
            Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
            FirestoreTransactionRunner transactions =
                    new FirestoreTransactionRunner(firestore);
            FirestoreIdGenerator ids = new FirestoreIdGenerator(
                    firestore,
                    transactions
            );
            FirestoreAreaRepository areas = new FirestoreAreaRepository(
                    firestore,
                    transactions,
                    ids,
                    clock
            );
            FirestoreVisitRepository visits = new FirestoreVisitRepository(
                    firestore,
                    transactions,
                    ids,
                    areas,
                    clock
            );
            FirestoreReportRepository reports = new FirestoreReportRepository(
                    firestore,
                    transactions,
                    ids,
                    clock
            );
            FirestoreReportAreaRepository reportAreas =
                    new FirestoreReportAreaRepository(
                            firestore,
                            transactions,
                            reports,
                            areas
                    );
            FirestoreLineWebhookEventRepository events =
                    new FirestoreLineWebhookEventRepository(
                            firestore,
                            transactions,
                            clock
                    );
            FirestoreLineVisitDraftRepository drafts =
                    new FirestoreLineVisitDraftRepository(
                            firestore,
                            transactions,
                            ids,
                            areas,
                            visits,
                            clock
                    );
            AreaServiceImpl areaService = new AreaServiceImpl(areas, clock);
            VisitServiceImpl visitService = new VisitServiceImpl(visits, areas);
            LineVisitDraftActionService draftActions =
                    new LineVisitDraftActionService(
                            drafts,
                            areas,
                            areaService,
                            visitService,
                            transactions,
                            clock,
                            ZoneOffset.ofHours(9)
                    );

            AreaEntity centerMinami = areas.save(area(
                    "센터미나미",
                    "가나가와현",
                    "요코하마시 츠즈키구"
            ));
            AreaEntity kichijoji = areas.save(area(
                    "키치조지",
                    "도쿄도",
                    "무사시노시"
            ));

            assertEquals(1L, centerMinami.getId());
            assertEquals(2L, kichijoji.getId());
            assertEquals(2L, areas.countByDeletedAtIsNull());
            assertThrows(DuplicateDocumentException.class, () -> areas.save(
                    area("센터미나미", "가나가와현", "요코하마시 츠즈키구")
            ));

            VisitEntity visit = visits.save(VisitEntity.builder()
                    .area(centerMinami)
                    .visitDate(LocalDate.parse("2026-08-20"))
                    .atmosphereScore(8)
                    .infraScore(9)
                    .cleanScore(8)
                    .sizeScore(7)
                    .accessScore(8)
                    .memo("Firestore 통합 테스트")
                    .build());

            assertEquals(1L, visit.getId());
            assertEquals(1, visits.findAllForActiveAreas().size());
            assertEquals("센터미나미", visits.findById(visit.getId())
                    .orElseThrow()
                    .getArea()
                    .getName());

            ReportEntity report = reports.save(ReportEntity.builder()
                    .reportType(ReportType.COMPARE)
                    .model("gpt-5.6-luna")
                    .promptVersion("compare-v1")
                    .sourceFingerprint("fingerprint-1")
                    .sourceWebhookEventId("report-event-1")
                    .build());
            report.assignStoragePath("reports/v1/compare/1.md");
            reportAreas.saveAll(List.of(
                    new ReportAreaEntity(report, centerMinami, 1),
                    new ReportAreaEntity(report, kichijoji, 2)
            ));
            reports.save(report);

            List<ReportAreaEntity> loadedTargets =
                    reportAreas.findAllWithAreaByReportId(report.getId());
            assertEquals(List.of("센터미나미", "키치조지"), loadedTargets
                    .stream()
                    .map(target -> target.getArea().getName())
                    .toList());
            assertEquals("reports/v1/compare/1.md", reports
                    .findById(report.getId())
                    .orElseThrow()
                    .getStoragePath());
            assertThrows(DuplicateDocumentException.class, () -> reports.save(
                    ReportEntity.builder()
                            .reportType(ReportType.COMPARE)
                            .model("gpt-5.6-luna")
                            .promptVersion("compare-v1")
                            .sourceFingerprint("fingerprint-2")
                            .sourceWebhookEventId("report-event-1")
                            .build()
            ));

            LineWebhookEventEntity event = LineWebhookEventEntity.received(
                    new LineWebhookEventPayload(
                            "line-event-1",
                            "line-user-1",
                            LineWebhookEventType.TEXT_MESSAGE,
                            "센터미나미를 방문했어",
                            null,
                            NOW
                    )
            );
            events.save(event);
            assertEquals("line-user-1", events.findById("line-event-1")
                    .orElseThrow()
                    .getLineUserId());

            LineVisitDraftEntity draft = lineDraft(
                    centerMinami,
                    "line-event-1"
            );
            drafts.save(draft);

            LineVisitDraftEntity loadedDraft = drafts
                    .findBySourceWebhookEventId("line-event-1")
                    .orElseThrow();
            assertNotNull(loadedDraft.getId());
            assertEquals(centerMinami.getId(), loadedDraft.getArea().getId());
            assertTrue(loadedDraft.getWarnings().isEmpty());

            draftActions.execute(
                    new LineDraftCommand(
                            LineDraftAction.CONFIRM,
                            loadedDraft.getId()
                    ),
                    "line-user-1"
            );
            LineVisitDraftEntity confirmedDraft = drafts
                    .findBySourceWebhookEventId("line-event-1")
                    .orElseThrow();
            assertNotNull(confirmedDraft.getConfirmedVisit());
            assertEquals(2, visits.findAllForActiveAreas().size());

            LineVisitDraftEntity secondDraft = lineDraft(
                    centerMinami,
                    "line-event-2"
            );
            LineVisitDraftEntity thirdDraft = lineDraft(
                    centerMinami,
                    "line-event-3"
            );
            drafts.saveAll(List.of(secondDraft, thirdDraft));
            assertEquals(draft.getId() + 1, secondDraft.getId());
            assertEquals(secondDraft.getId() + 1, thirdDraft.getId());

            centerMinami.softDelete(NOW.plusSeconds(1));
            areas.save(centerMinami);
            assertFalse(areas.findByIdAndDeletedAtIsNull(
                    centerMinami.getId()
            ).isPresent());
            assertTrue(visits.findAllForActiveAreas().isEmpty());
        } finally {
            firestore.close();
        }
    }

    private AreaEntity area(String name, String prefecture, String city) {
        return AreaEntity.builder()
                .name(name)
                .prefecture(prefecture)
                .city(city)
                .station(name + "역")
                .build();
    }

    private LineVisitDraftEntity lineDraft(
            AreaEntity area,
            String sourceWebhookEventId
    ) {
        return LineVisitDraftEntity.create(
                sourceWebhookEventId,
                "line-user-1",
                area,
                false,
                new VisitDraftResponse(
                        new VisitDraftAreaResponse(
                                area.getId(),
                                area.getName(),
                                area.getPrefecture(),
                                area.getCity(),
                                area.getStation()
                        ),
                        LocalDate.parse("2026-08-20"),
                        8,
                        9,
                        8,
                        7,
                        8,
                        "초안",
                        List.of()
                ),
                List.of(),
                NOW
        );
    }
}
