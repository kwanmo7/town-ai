package com.townai.report.generation;

import com.townai.report.generation.ReportDataAssembler.AllInput;
import com.townai.report.generation.ReportDataAssembler.AreaInput;
import com.townai.report.generation.ReportDataAssembler.CompareInput;
import com.townai.report.generation.ReportDataAssembler.SummaryInput;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptEvaluationFixtureTest {

    private static final String FIXTURE_BASE_PATH = "prompt-eval/fixtures/";

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Test
    void deserializesAllPromptEvaluationFixtures() throws IOException {
        SummaryInput summary = readFixture("summary.json", SummaryInput.class);
        AreaInput area = readFixture("area.json", AreaInput.class);
        CompareInput compare = readFixture("compare.json", CompareInput.class);
        AllInput all = readFixture("all.json", AllInput.class);

        assertEquals(6, summary.areaCount());
        assertFalse(summary.top5().atmosphere().isEmpty());

        assertEquals(area.statistics().visitCount(), area.visits().size());
        assertTrue(area.visits().size() > 1);

        assertTrue(compare.areas().size() >= 2 && compare.areas().size() <= 5);
        assertEquals(
                compare.areas().size(),
                compare.areas().stream().map(item -> item.id()).distinct().count()
        );

        assertFalse(all.areas().isEmpty());
        assertTrue(all.areas().stream().allMatch(item -> !item.visits().isEmpty()));
        assertTrue(all.areas().stream().anyMatch(item -> item.visitCount() == 1));
        assertTrue(all.areas().stream()
                .allMatch(item -> item.visitCount() == item.visits().size()));
    }

    private <T> T readFixture(String filename, Class<T> type) throws IOException {
        try (var inputStream = new ClassPathResource(
                FIXTURE_BASE_PATH + filename
        ).getInputStream()) {
            return objectMapper.readValue(inputStream, type);
        }
    }
}
