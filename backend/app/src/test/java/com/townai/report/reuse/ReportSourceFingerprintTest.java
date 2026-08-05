package com.townai.report.reuse;

import com.townai.area.entity.AreaEntity;
import com.townai.common.openai.OpenAiProperties;
import com.townai.report.entity.ReportType;
import com.townai.report.generation.ReportGenerationData;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ReportSourceFingerprintTest {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();
    private final ReportSourceFingerprint fingerprint =
            new ReportSourceFingerprint(
                    objectMapper,
                    new OpenAiProperties(
                            "key",
                            "https://api.openai.com/v1",
                            "gpt-test",
                            Duration.ofSeconds(2),
                            Duration.ofSeconds(10)
                    )
            );

    @Test
    void returnsStableFingerprintForSameReportInput() {
        ReportGenerationData first = data(8);
        ReportGenerationData second = data(8);

        String firstFingerprint = fingerprint.calculate(first);
        String secondFingerprint = fingerprint.calculate(second);

        assertEquals(64, firstFingerprint.length());
        assertEquals(firstFingerprint, secondFingerprint);
    }

    @Test
    void changesFingerprintWhenPromptInputChanges() {
        String before = fingerprint.calculate(data(8));
        String after = fingerprint.calculate(data(9));

        assertNotEquals(before, after);
    }

    @Test
    void changesFingerprintWhenReportModelChanges() {
        ReportSourceFingerprint otherModel = new ReportSourceFingerprint(
                objectMapper,
                new OpenAiProperties(
                        "key",
                        "https://api.openai.com/v1",
                        "gpt-other",
                        Duration.ofSeconds(2),
                        Duration.ofSeconds(10)
                )
        );

        String before = fingerprint.calculate(data(8));
        String after = otherModel.calculate(data(8));

        assertNotEquals(before, after);
    }

    private ReportGenerationData data(int atmosphereScore) {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
        ReflectionTestUtils.setField(area, "id", 1L);
        return new ReportGenerationData(
                ReportType.AREA,
                List.of(area),
                Map.of("atmosphereScore", atmosphereScore)
        );
    }
}
