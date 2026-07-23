package com.townai.report.storage;

import com.townai.area.entity.AreaEntity;
import com.townai.report.entity.ReportType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalReportStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void storesLogicalReportsPathBelowConfiguredLocalDirectory() {
        LocalReportStorage storage = new LocalReportStorage(tempDirectory.toString());
        String storagePath = "reports/v1/area/센터미나미_2026-07-24_10.md";

        storage.write(storagePath, "# 리포트");

        Path expectedFile = tempDirectory.resolve(
                "v1/area/센터미나미_2026-07-24_10.md"
        );
        assertTrue(Files.exists(expectedFile));
        assertEquals("# 리포트", storage.read(storagePath));

        storage.delete(storagePath);
        assertFalse(Files.exists(expectedFile));
        storage.delete(storagePath);
    }

    @Test
    void rejectsPathTraversal() {
        LocalReportStorage storage = new LocalReportStorage(tempDirectory.toString());

        assertThrows(
                ReportStorageException.class,
                () -> storage.write("reports/../../outside.md", "content")
        );
    }

    @Test
    void createsPathUsingReportTypeTargetNamesDateAndId() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-24T23:59:59Z"),
                ZoneOffset.UTC
        );
        ReportStoragePathFactory factory = new ReportStoragePathFactory(clock);
        AreaEntity first = createArea("센터 미나미");
        AreaEntity second = createArea("타마/플라자");

        assertEquals(
                "reports/v1/compare/센터-미나미-타마-플라자_2026-07-24_11.md",
                factory.create(ReportType.COMPARE, 11L, List.of(first, second))
        );
        assertEquals(
                "reports/v1/summary/2026-07-24_12.md",
                factory.create(ReportType.SUMMARY, 12L, List.of())
        );
    }

    private AreaEntity createArea(String name) {
        return AreaEntity.builder()
                .name(name)
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
    }
}
