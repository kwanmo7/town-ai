package com.townai.report.reuse;

import com.townai.common.openai.OpenAiProperties;
import com.townai.report.generation.ReportGenerationData;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Report 결과에 영향을 주는 모델, Prompt 버전과 입력을 SHA-256 지문으로 변환한다.
 */
@Component
public class ReportSourceFingerprint {

    private final ObjectMapper objectMapper;
    private final String reportModel;

    /**
     * 직렬화 설정을 애플리케이션의 공통 JSON 규칙과 일치시킨다.
     *
     * @param objectMapper Prompt 입력 직렬화에 사용할 ObjectMapper
     * @param openAiProperties Report 생성에 사용할 모델 설정
     */
    public ReportSourceFingerprint(
            ObjectMapper objectMapper,
            OpenAiProperties openAiProperties
    ) {
        this.objectMapper = objectMapper;
        this.reportModel = openAiProperties.reportModel();
    }

    /**
     * 유형·모델·Prompt 버전·대상 순서·실제 Prompt 입력을 하나의 지문으로 계산한다.
     *
     * @param data 검증과 DB 조회가 완료된 Report 입력
     * @return 64자 소문자 SHA-256 Hex 문자열
     */
    public String calculate(ReportGenerationData data) {
        List<Long> targetAreaIds = data.targetAreas().stream()
                .map(area -> area.getId())
                .toList();
        FingerprintSource source = new FingerprintSource(
                data.reportType().name(),
                data.reportType().promptVersion(),
                reportModel,
                targetAreaIds,
                data.promptInput()
        );
        try {
            byte[] serialized = objectMapper.writeValueAsBytes(source);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(serialized));
        } catch (JacksonException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Failed to calculate Report source fingerprint.",
                    exception
            );
        }
    }

    private record FingerprintSource(
            String reportType,
            String promptVersion,
            String reportModel,
            List<Long> targetAreaIds,
            Object promptInput
    ) {
    }
}
