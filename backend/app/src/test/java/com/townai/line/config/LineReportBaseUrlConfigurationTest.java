package com.townai.line.config;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.boot.env.YamlPropertySourceLoader;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LineReportBaseUrlConfigurationTest {

    private static final String PROPERTY_NAME =
            "town-ai.line.report-base-url";

    @Test
    void usesExplicitReportBaseUrlFirst() throws IOException {
        StandardEnvironment environment = environment(Map.of(
                "LINE_REPORT_BASE_URL",
                "https://reports.example.com",
                "LINE_CLOUD_TASKS_OIDC_AUDIENCE",
                "https://tasks.example.com"
        ));

        assertEquals(
                "https://reports.example.com",
                environment.getProperty(PROPERTY_NAME)
        );
    }

    @Test
    void fallsBackToCloudTasksAudienceInProduction() throws IOException {
        StandardEnvironment environment = environment(Map.of(
                "LINE_CLOUD_TASKS_OIDC_AUDIENCE",
                "https://town-ai-api.example.com"
        ));

        assertEquals(
                "https://town-ai-api.example.com",
                environment.getProperty(PROPERTY_NAME)
        );
    }

    @Test
    void usesLocalhostWhenNoPublicUrlIsConfigured() throws IOException {
        StandardEnvironment environment = environment(Map.of());

        assertEquals(
                "http://localhost:8080",
                environment.getProperty(PROPERTY_NAME)
        );
    }

    private StandardEnvironment environment(
            Map<String, Object> overrides
    ) throws IOException {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(
                new MapPropertySource("test-overrides", overrides)
        );
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(
                "application-yaml",
                new ClassPathResource("application.yml")
        ).forEach(environment.getPropertySources()::addLast);
        return environment;
    }
}
