package com.townai.visit.service;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.visit.dto.VisitRequest;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import com.townai.visit.service.impl.VisitServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VisitServiceImplTest {

    @Mock
    private VisitRepository visitRepository;

    @Mock
    private AreaRepository areaRepository;

    private VisitService visitService;

    @BeforeEach
    void setUp() {
        visitService = new VisitServiceImpl(visitRepository, areaRepository);
    }

    @Test
    void createsVisitForActiveAreaAndNormalizesMemo() {
        AreaEntity area = createArea("센터미나미");
        VisitRequest request = createRequest(1L, "  재방문 메모  ");
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));
        when(visitRepository.saveAndFlush(any(VisitEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        visitService.create(request);

        ArgumentCaptor<VisitEntity> captor = ArgumentCaptor.forClass(VisitEntity.class);
        verify(visitRepository).saveAndFlush(captor.capture());
        VisitEntity saved = captor.getValue();
        assertSame(area, saved.getArea());
        assertEquals("재방문 메모", saved.getMemo());
        assertEquals(9, saved.getAtmosphereScore());
    }

    @Test
    void convertsBlankMemoToNull() {
        AreaEntity area = createArea("센터미나미");
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));
        when(visitRepository.saveAndFlush(any(VisitEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        visitService.create(createRequest(1L, "  "));

        ArgumentCaptor<VisitEntity> captor = ArgumentCaptor.forClass(VisitEntity.class);
        verify(visitRepository).saveAndFlush(captor.capture());
        assertNull(captor.getValue().getMemo());
    }

    @Test
    void rejectsMissingOrDeletedAreaWhenCreatingVisit() {
        when(areaRepository.findByIdAndDeletedAtIsNull(99L))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> visitService.create(createRequest(99L, null))
        );

        assertEquals(ErrorCode.AREA_NOT_FOUND, exception.errorCode());
        verify(visitRepository, never()).saveAndFlush(any());
    }

    @Test
    void rejectsInvalidDateRange() {
        LocalDate from = LocalDate.of(2026, 7, 31);
        LocalDate to = LocalDate.of(2026, 7, 1);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> visitService.findAll(null, from, to)
        );

        assertEquals(ErrorCode.INVALID_DATE_RANGE, exception.errorCode());
        verify(visitRepository, never()).findAllByFilters(any(), any(), any());
    }

    @Test
    void delegatesAreaFilterToRepositorySoUnknownAreaCanReturnEmptyList() {
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        when(visitRepository.findAllByFilters(99L, from, to))
                .thenReturn(List.of());

        assertEquals(0, visitService.findAll(99L, from, to).size());
        verify(visitRepository).findAllByFilters(99L, from, to);
    }

    @Test
    void hardDeletesExistingVisit() {
        VisitEntity visit = createVisit(createArea("센터미나미"));
        when(visitRepository.findById(1L)).thenReturn(Optional.of(visit));

        visitService.delete(1L);

        verify(visitRepository).delete(visit);
    }

    private VisitRequest createRequest(Long areaId, String memo) {
        return new VisitRequest(
                areaId,
                LocalDate.of(2026, 7, 24),
                9,
                8,
                7,
                6,
                5,
                memo
        );
    }

    private AreaEntity createArea(String name) {
        return AreaEntity.builder()
                .name(name)
                .prefecture("가나가와현")
                .city("요코하마시")
                .build();
    }

    private VisitEntity createVisit(AreaEntity area) {
        return VisitEntity.builder()
                .area(area)
                .visitDate(LocalDate.of(2026, 7, 24))
                .atmosphereScore(9)
                .infraScore(8)
                .cleanScore(7)
                .sizeScore(6)
                .accessScore(5)
                .build();
    }
}
