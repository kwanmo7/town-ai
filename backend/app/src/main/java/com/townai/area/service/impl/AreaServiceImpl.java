package com.townai.area.service.impl;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.dto.AreaRequest;
import com.townai.area.dto.AreaSummaryResponse;
import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.area.service.AreaService;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Area 문자열 정규화, 위치 중복 검사와 Transaction 경계를 구현한다.
 *
 * <p>공백 정리 후 애플리케이션에서 먼저 중복을 확인하고, 동시 요청은 DB UNIQUE
 * 제약과 {@code saveAndFlush}/{@code flush}로 최종 방어한다. 삭제는 Visit 이력을
 * 보존하기 위해 UTC 초 단위 시각을 기록하는 논리 삭제 방식이다.</p>
 */
@Service
@Transactional(readOnly = true)
public class AreaServiceImpl implements AreaService {

    private final AreaRepository areaRepository;
    private final Clock clock;

    /**
     * Area Service를 생성한다.
     *
     * @param areaRepository Area 저장과 활성 상태 조회를 담당할 Repository
     * @param clock 논리 삭제 시각을 생성할 UTC Clock
     */
    public AreaServiceImpl(AreaRepository areaRepository, Clock clock) {
        this.areaRepository = areaRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public AreaDetailResponse create(AreaRequest request) {
        NormalizedArea normalized = normalize(request);
        validateDuplicate(normalized);

        AreaEntity area = AreaEntity.builder()
                .name(normalized.name())
                .prefecture(normalized.prefecture())
                .city(normalized.city())
                .station(normalized.station())
                .build();

        try {
            return AreaDetailResponse.from(areaRepository.saveAndFlush(area));
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.AREA_ALREADY_EXISTS);
        }
    }

    @Override
    public List<AreaSummaryResponse> findAll() {
        return areaRepository.findAllByDeletedAtIsNullOrderByIdAsc()
                .stream()
                .map(AreaSummaryResponse::from)
                .toList();
    }

    @Override
    public AreaDetailResponse findById(Long areaId) {
        return AreaDetailResponse.from(findActiveArea(areaId));
    }

    @Override
    @Transactional
    public AreaDetailResponse update(Long areaId, AreaRequest request) {
        AreaEntity area = findActiveArea(areaId);
        NormalizedArea normalized = normalize(request);

        if (areaRepository.existsByPrefectureAndCityAndNameAndIdNot(
                normalized.prefecture(),
                normalized.city(),
                normalized.name(),
                areaId
        )) {
            throw new ApiException(ErrorCode.AREA_ALREADY_EXISTS);
        }

        area.update(
                normalized.name(),
                normalized.prefecture(),
                normalized.city(),
                normalized.station()
        );

        try {
            areaRepository.flush();
            return AreaDetailResponse.from(area);
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(ErrorCode.AREA_ALREADY_EXISTS);
        }
    }

    @Override
    @Transactional
    public void delete(Long areaId) {
        AreaEntity area = findActiveArea(areaId);
        area.softDelete(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
    }

    private AreaEntity findActiveArea(Long areaId) {
        return areaRepository.findByIdAndDeletedAtIsNull(areaId)
                .orElseThrow(() -> new ApiException(ErrorCode.AREA_NOT_FOUND));
    }

    /**
     * 사전 조회로 일반 중복을 처리하고 DB UNIQUE 제약으로 동시 요청의 중복을 최종 방어한다.
     */
    private void validateDuplicate(NormalizedArea area) {
        if (areaRepository.existsByPrefectureAndCityAndName(
                area.prefecture(),
                area.city(),
                area.name()
        )) {
            throw new ApiException(ErrorCode.AREA_ALREADY_EXISTS);
        }
    }

    private NormalizedArea normalize(AreaRequest request) {
        return new NormalizedArea(
                request.name().trim(),
                request.prefecture().trim(),
                request.city().trim(),
                normalizeOptional(request.station())
        );
    }

    private String normalizeOptional(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record NormalizedArea(
            String name,
            String prefecture,
            String city,
            String station
    ) {
    }
}
