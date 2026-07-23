package com.townai.visit.controller;

import com.townai.visit.dto.VisitDetailResponse;
import com.townai.visit.dto.VisitMutationResponse;
import com.townai.visit.dto.VisitRequest;
import com.townai.visit.dto.VisitSummaryResponse;
import com.townai.visit.service.VisitService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * Visit 생성, 조건 조회, 전체 수정과 물리 삭제를 노출하는 REST Controller이다.
 *
 * <p>목록의 날짜 범위는 양 끝 날짜를 모두 포함한다. Area 필터가 없으면 이력 보존을
 * 위해 논리 삭제된 Area의 기존 Visit도 조회할 수 있다.</p>
 */
@RestController
@RequestMapping("/api/visits")
public class VisitController {

    private final VisitService visitService;

    /**
     * Visit Controller를 생성한다.
     *
     * @param visitService Visit Use Case를 처리할 Service
     */
    public VisitController(VisitService visitService) {
        this.visitService = visitService;
    }

    /**
     * 활성 Area에 새 방문 평가를 등록한다.
     *
     * @param request Area ID, 방문일, 다섯 점수와 선택 메모
     * @return {@code 201 Created}, 생성 위치와 생성 결과
     */
    @PostMapping
    public ResponseEntity<VisitMutationResponse> create(
            @Valid @RequestBody VisitRequest request
    ) {
        VisitMutationResponse response = visitService.create(request);
        return ResponseEntity
                .created(URI.create("/api/visits/" + response.id()))
                .body(response);
    }

    /**
     * 선택 조건에 맞는 Visit을 최근 방문 순으로 조회한다.
     *
     * @param areaId 선택적인 활성 Area ID
     * @param from 포함되는 방문일 하한
     * @param to 포함되는 방문일 상한
     * @return 방문일 내림차순, 동률이면 ID 내림차순인 목록
     */
    @GetMapping
    public List<VisitSummaryResponse> findAll(
            @RequestParam(required = false) Long areaId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to
    ) {
        return visitService.findAll(areaId, from, to);
    }

    /**
     * Visit 한 건과 연결된 Area 상세 정보를 조회한다.
     *
     * @param visitId 조회할 Visit ID
     * @return Visit 상세 응답
     */
    @GetMapping("/{visitId}")
    public VisitDetailResponse findById(@PathVariable Long visitId) {
        return visitService.findById(visitId);
    }

    /**
     * Visit의 모든 입력 필드를 PUT 방식으로 교체한다.
     *
     * @param visitId 수정할 Visit ID
     * @param request 교체할 전체 Visit 값
     * @return 수정된 Visit
     */
    @PutMapping("/{visitId}")
    public VisitMutationResponse update(
            @PathVariable Long visitId,
            @Valid @RequestBody VisitRequest request
    ) {
        return visitService.update(visitId, request);
    }

    /**
     * Visit 한 건을 물리 삭제한다.
     *
     * @param visitId 삭제할 Visit ID
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{visitId}")
    public ResponseEntity<Void> delete(@PathVariable Long visitId) {
        visitService.delete(visitId);
        return ResponseEntity.noContent().build();
    }
}
