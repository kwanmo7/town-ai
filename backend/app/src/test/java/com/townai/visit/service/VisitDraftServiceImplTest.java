package com.townai.visit.service;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.parser.VisitParserAiClient;
import com.townai.visit.parser.VisitParserInput;
import com.townai.visit.parser.validation.VisitDraftOutputValidator;
import com.townai.visit.service.impl.VisitDraftServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitDraftServiceImplTest {

    @Mock
    private AreaRepository areaRepository;

    private AreaEntity area;

    @BeforeEach
    void setUp() {
        area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        ReflectionTestUtils.setField(area, "id", 1L);
        when(areaRepository.findAllByDeletedAtIsNullOrderByIdAsc())
                .thenReturn(List.of(area));
    }

    @Test
    void returnsReviewableDraftWithoutSavingVisit() {
        FakeVisitParserAiClient aiClient = new FakeVisitParserAiClient("""
                {
                  "area": {"id": 1, "name": "센터미나미"},
                  "visitDate": "2026-07-24",
                  "atmosphereScore": 9,
                  "infraScore": null,
                  "cleanScore": null,
                  "sizeScore": null,
                  "accessScore": 7,
                  "memo": null,
                  "warnings": [
                    "생활 인프라, 청결도, 넓은 집 가능성 점수가 입력되지 않았습니다."
                  ]
                }
                """);
        VisitDraftService service = service(aiClient);

        VisitDraftResponse result = service.create(new VisitDraftRequest(
                "  오늘 센터미나미 분위기 9점, 접근성 7점  "
        ));

        assertEquals(1L, result.area().id());
        assertEquals("센터미나미", result.area().name());
        assertEquals("2026-07-24", result.visitDate().toString());
        assertEquals(9, result.atmosphereScore());
        assertNull(result.infraScore());
        assertEquals(7, result.accessScore());
        assertEquals(1, result.warnings().size());
        assertEquals(1, aiClient.inputs.size());
        assertEquals("오늘 센터미나미 분위기 9점, 접근성 7점", aiClient.inputs.getFirst().text());
        assertEquals("2026-07-24", aiClient.inputs.getFirst().currentDate().toString());
    }

    @Test
    void retriesOnceWhenAreaDoesNotMatchActiveArea() {
        FakeVisitParserAiClient aiClient = new FakeVisitParserAiClient(
                completeOutput(99L, "존재하지 않는 지역"),
                completeOutput(1L, "센터미나미")
        );
        VisitDraftService service = service(aiClient);

        VisitDraftResponse result = service.create(
                new VisitDraftRequest("센터미나미 방문 평가")
        );

        assertEquals(1L, result.area().id());
        assertEquals(2, aiClient.correctionInstructions.size());
        assertEquals(
                "Parser Area ID 또는 이름이 입력 Area와 일치하지 않습니다.",
                aiClient.correctionInstructions.get(1)
        );
    }

    @Test
    void failsWhenCorrectedOutputIsStillInvalid() {
        FakeVisitParserAiClient aiClient = new FakeVisitParserAiClient(
                completeOutput(99L, "잘못된 지역"),
                completeOutput(98L, "여전히 잘못된 지역")
        );
        VisitDraftService service = service(aiClient);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> service.create(new VisitDraftRequest("방문 평가"))
        );

        assertEquals(ErrorCode.OPENAI_API_ERROR, exception.errorCode());
        assertEquals(2, aiClient.inputs.size());
    }

    @Test
    void retriesWhenRequiredValuesAreMissingWithoutWarning() {
        String invalid = completeOutput(1L, "센터미나미")
                .replace("\"infraScore\": 8", "\"infraScore\": null");
        String corrected = invalid.replace(
                "\"warnings\": []",
                "\"warnings\": [\"생활 인프라 점수가 입력되지 않았습니다.\"]"
        );
        FakeVisitParserAiClient aiClient = new FakeVisitParserAiClient(
                invalid,
                corrected
        );
        VisitDraftService service = service(aiClient);

        VisitDraftResponse result = service.create(
                new VisitDraftRequest("센터미나미 방문 평가")
        );

        assertNull(result.infraScore());
        assertEquals(1, result.warnings().size());
        assertEquals(2, aiClient.inputs.size());
    }

    private VisitDraftService service(FakeVisitParserAiClient aiClient) {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-23T16:30:00Z"),
                ZoneOffset.UTC
        );
        return new VisitDraftServiceImpl(
                areaRepository,
                aiClient,
                new VisitDraftOutputValidator(
                        JsonMapper.builder().findAndAddModules().build()
                ),
                clock,
                ZoneId.of("Asia/Tokyo")
        );
    }

    private String completeOutput(Long areaId, String areaName) {
        return """
                {
                  "area": {"id": %d, "name": "%s"},
                  "visitDate": "2026-07-24",
                  "atmosphereScore": 8,
                  "infraScore": 8,
                  "cleanScore": 8,
                  "sizeScore": 8,
                  "accessScore": 8,
                  "memo": "방문 메모",
                  "warnings": []
                }
                """.formatted(areaId, areaName);
    }

    private static class FakeVisitParserAiClient implements VisitParserAiClient {

        private final Queue<String> outputs;
        private final List<VisitParserInput> inputs = new ArrayList<>();
        private final List<String> correctionInstructions = new ArrayList<>();

        private FakeVisitParserAiClient(String... outputs) {
            this.outputs = new ArrayDeque<>(List.of(outputs));
        }

        @Override
        public String parse(
                VisitParserInput input,
                String correctionInstruction
        ) {
            inputs.add(input);
            correctionInstructions.add(correctionInstruction);
            return outputs.remove();
        }
    }
}
