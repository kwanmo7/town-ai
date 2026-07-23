package com.townai.area.service;

import com.townai.area.dto.AreaRequest;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.area.service.impl.AreaServiceImpl;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AreaServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-07-23T10:20:30Z");

    @Mock
    private AreaRepository areaRepository;

    private AreaService areaService;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        areaService = new AreaServiceImpl(areaRepository, clock);
    }

    @Test
    void createsAreaAfterNormalizingStrings() {
        AreaRequest request = new AreaRequest(
                "  센터미나미  ",
                "  가나가와현  ",
                "  요코하마시  ",
                "   "
        );
        when(areaRepository.saveAndFlush(any(AreaEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        areaService.create(request);

        ArgumentCaptor<AreaEntity> captor = ArgumentCaptor.forClass(AreaEntity.class);
        verify(areaRepository).saveAndFlush(captor.capture());
        AreaEntity saved = captor.getValue();
        assertEquals("센터미나미", saved.getName());
        assertEquals("가나가와현", saved.getPrefecture());
        assertEquals("요코하마시", saved.getCity());
        assertNull(saved.getStation());
    }

    @Test
    void rejectsDuplicateArea() {
        AreaRequest request = new AreaRequest(
                "센터미나미",
                "가나가와현",
                "요코하마시",
                null
        );
        when(areaRepository.existsByPrefectureAndCityAndName(
                "가나가와현",
                "요코하마시",
                "센터미나미"
        )).thenReturn(true);

        ApiException exception = assertThrows(
                ApiException.class,
                () -> areaService.create(request)
        );

        assertEquals(ErrorCode.AREA_ALREADY_EXISTS, exception.errorCode());
    }

    @Test
    void doesNotReturnDeletedOrMissingArea() {
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.empty());

        ApiException exception = assertThrows(
                ApiException.class,
                () -> areaService.findById(1L)
        );

        assertEquals(ErrorCode.AREA_NOT_FOUND, exception.errorCode());
    }

    @Test
    void softDeletesAreaUsingUtcClock() {
        AreaEntity area = AreaEntity.builder()
                .name("센터미나미")
                .prefecture("가나가와현")
                .city("요코하마시")
                .station("센터미나미역")
                .build();
        when(areaRepository.findByIdAndDeletedAtIsNull(1L))
                .thenReturn(Optional.of(area));

        areaService.delete(1L);

        assertEquals(NOW, area.getDeletedAt());
    }
}
