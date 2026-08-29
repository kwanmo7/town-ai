package com.townai.visit.service.impl;

import com.townai.area.entity.AreaEntity;
import com.townai.area.repository.AreaRepository;
import com.townai.common.error.ApiException;
import com.townai.common.error.ErrorCode;
import com.townai.visit.dto.VisitDetailResponse;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.dto.VisitRequest;
import com.townai.visit.dto.VisitSummaryResponse;
import com.townai.visit.entity.VisitEntity;
import com.townai.visit.repository.VisitRepository;
import com.townai.visit.service.VisitService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * Visit의 활성 Area 연결, 날짜 범위 검증, 메모 정규화와 Transaction 경계를 구현한다.
 *
 * <p>Visit 생성·수정 시 논리 삭제된 Area는 선택할 수 없다. 목록에서도 삭제된
 * Area의 Visit을 제외하고 Repository가 결정한 안정적인 정렬 순서를 유지한다.
 * Visit 자체의 삭제는 원본 Row를 제거하는 물리 삭제 방식이다.</p>
 */
@Service
public class VisitServiceImpl implements VisitService {

    private final VisitRepository visitRepository;
    private final AreaRepository areaRepository;

    /**
     * Visit Service를 생성한다.
     *
     * @param visitRepository Visit 저장과 조회 Repository
     * @param areaRepository 활성 Area 검증 Repository
     */
    public VisitServiceImpl(
            VisitRepository visitRepository,
            AreaRepository areaRepository
    ) {
        this.visitRepository = visitRepository;
        this.areaRepository = areaRepository;
    }

    @Override
    public VisitMutationResponse create(VisitRequest request) {
        AreaEntity area = findActiveArea(request.areaId());
        VisitEntity visit = VisitEntity.builder()
                .area(area)
                .visitDate(request.visitDate())
                .atmosphereScore(request.atmosphereScore())
                .infraScore(request.infraScore())
                .cleanScore(request.cleanScore())
                .sizeScore(request.sizeScore())
                .accessScore(request.accessScore())
                .memo(normalizeMemo(request.memo()))
                .build();

        return VisitMutationResponse.from(visitRepository.save(visit));
    }

    @Override
    public List<VisitSummaryResponse> findAll(
            Long areaId,
            LocalDate from,
            LocalDate to
    ) {
        validateDateRange(from, to);
        return visitRepository.findAllByFilters(areaId, from, to)
                .stream()
                .map(VisitSummaryResponse::from)
                .toList();
    }

    @Override
    public VisitDetailResponse findById(Long visitId) {
        return VisitDetailResponse.from(findVisit(visitId));
    }

    @Override
    public VisitMutationResponse update(Long visitId, VisitRequest request) {
        VisitEntity visit = findVisit(visitId);
        AreaEntity area = findActiveArea(request.areaId());

        visit.update(
                area,
                request.visitDate(),
                request.atmosphereScore(),
                request.infraScore(),
                request.cleanScore(),
                request.sizeScore(),
                request.accessScore(),
                normalizeMemo(request.memo())
        );
        return VisitMutationResponse.from(visitRepository.save(visit));
    }

    @Override
    public void delete(Long visitId) {
        visitRepository.delete(findVisit(visitId));
    }

    private VisitEntity findVisit(Long visitId) {
        return visitRepository.findById(visitId)
                .orElseThrow(() -> new ApiException(ErrorCode.VISIT_NOT_FOUND));
    }

    private AreaEntity findActiveArea(Long areaId) {
        return areaRepository.findByIdAndDeletedAtIsNull(areaId)
                .orElseThrow(() -> new ApiException(ErrorCode.AREA_NOT_FOUND));
    }

    private void validateDateRange(LocalDate from, LocalDate to) {
        if (from != null && to != null && from.isAfter(to)) {
            throw new ApiException(ErrorCode.INVALID_DATE_RANGE);
        }
    }

    private String normalizeMemo(String memo) {
        if (memo == null || memo.isBlank()) {
            return null;
        }
        return memo.trim();
    }
}
