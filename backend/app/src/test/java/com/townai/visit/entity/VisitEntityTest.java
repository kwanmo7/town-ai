package com.townai.visit.entity;

import com.townai.area.entity.AreaEntity;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class VisitEntityTest {

    @Test
    void createsVisitUsingBuilder() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();

        VisitEntity visit = VisitEntity.builder()
                .area(area)
                .visitDate(LocalDate.of(2026, 7, 24))
                .atmosphereScore(9)
                .infraScore(10)
                .cleanScore(8)
                .sizeScore(7)
                .accessScore(9)
                .memo("살기 편리한 지역")
                .build();

        assertSame(area, visit.getArea());
        assertEquals(LocalDate.of(2026, 7, 24), visit.getVisitDate());
        assertEquals(9, visit.getAtmosphereScore());
        assertEquals("살기 편리한 지역", visit.getMemo());
    }

    @Test
    void updatesManagedValuesWithoutCreatingNewEntity() {
        AreaEntity originalArea = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
        AreaEntity changedArea = AreaEntity.builder()
                .name("센터키타")
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
        VisitEntity visit = VisitEntity.builder()
                .area(originalArea)
                .visitDate(LocalDate.of(2026, 7, 20))
                .atmosphereScore(5)
                .infraScore(5)
                .cleanScore(5)
                .sizeScore(5)
                .accessScore(5)
                .build();

        visit.update(
                changedArea,
                LocalDate.of(2026, 7, 24),
                6,
                7,
                8,
                9,
                10,
                "재방문 후 수정"
        );

        assertSame(changedArea, visit.getArea());
        assertEquals(LocalDate.of(2026, 7, 24), visit.getVisitDate());
        assertEquals(10, visit.getAccessScore());
        assertEquals("재방문 후 수정", visit.getMemo());
    }
}
