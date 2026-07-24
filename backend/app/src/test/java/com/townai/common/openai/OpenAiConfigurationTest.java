package com.townai.common.openai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiConfigurationTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withUserConfiguration(TestConfiguration.class)
                    .withPropertyValues(
                            "town-ai.openai.api-key=test-key",
                            "town-ai.openai.base-url=https://example.test/v1",
                            "town-ai.openai.report-model=test-model",
                            "town-ai.openai.connect-timeout=2s",
                            "town-ai.openai.read-timeout=30s"
                    );

    @Test
    void bindsTypedPropertiesAndCreatesResponsesClient() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(OpenAiProperties.class);
            assertThat(context).hasSingleBean(OpenAiResponsesClient.class);
            assertThat(context.getBean(OpenAiProperties.class).reportModel())
                    .isEqualTo("test-model");
        });
    }

    @Test
    void failsFastWhenRequiredHttpSettingIsInvalid() {
        contextRunner
                .withPropertyValues("town-ai.openai.read-timeout=0s")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            OpenAiConfiguration.class,
            OpenAiResponsesClient.class
    })
    static class TestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }
}
