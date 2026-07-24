package com.townai.report.storage;

import com.google.cloud.storage.Storage;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ReportStorageConditionTest {

    private final ApplicationContextRunner contextRunner =
            new ApplicationContextRunner()
                    .withBean(Storage.class, () -> mock(Storage.class))
                    .withUserConfiguration(
                            StorageImplementationsConfiguration.class
                    );

    @Test
    void usesLocalStorageByDefault() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ReportStorage.class);
            assertThat(context.getBean(ReportStorage.class))
                    .isInstanceOf(LocalReportStorage.class);
        });
    }

    @Test
    void usesGcsStorageOnlyWhenSelected() {
        contextRunner
                .withPropertyValues(
                        "town-ai.report-storage.type=gcs",
                        "town-ai.report-storage.bucket-name="
                                + "town-ai-reports-test"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ReportStorage.class);
                    assertThat(context.getBean(ReportStorage.class))
                            .isInstanceOf(GcsReportStorage.class);
                });
    }

    @Test
    void failsFastWhenGcsBucketIsMissing() {
        contextRunner
                .withPropertyValues("town-ai.report-storage.type=gcs")
                .run(context ->
                        assertThat(context).hasFailed()
                );
    }

    @Configuration(proxyBeanMethods = false)
    @Import({
            LocalReportStorage.class,
            GcsReportStorage.class,
            GcsStorageConfiguration.class
    })
    static class StorageImplementationsConfiguration {
    }
}
