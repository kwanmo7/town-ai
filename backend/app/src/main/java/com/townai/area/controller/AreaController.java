package com.townai.area.controller;

import com.townai.area.dto.AreaDetailResponse;
import com.townai.area.dto.AreaRequest;
import com.townai.area.dto.AreaSummaryResponse;
import com.townai.area.service.AreaService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/**
 * Area 생성, 조회, 전체 수정과 논리 삭제를 노출하는 REST Controller이다.
 *
 * <p>삭제된 Area는 목록·상세·수정 대상에서 제외되며, 삭제 요청은 연관 Visit을
 * 물리 삭제하지 않는다.</p>
 */
@RestController
@RequestMapping("/api/areas")
public class AreaController {

    private final AreaService areaService;

    /**
     * Area Controller를 생성한다.
     *
     * @param areaService Area Use Case를 처리할 Service
     */
    public AreaController(AreaService areaService) {
        this.areaService = areaService;
    }

    /**
     * 새로운 Area를 등록한다.
     *
     * @param request 위치 정보와 선택적인 인접 역
     * @return {@code 201 Created}, 생성 위치와 생성된 상세 응답
     */
    @PostMapping
    public ResponseEntity<AreaDetailResponse> create(
            @Valid @RequestBody AreaRequest request
    ) {
        AreaDetailResponse response = areaService.create(request);
        return ResponseEntity
                .created(URI.create("/api/areas/" + response.id()))
                .body(response);
    }

    /**
     * 논리 삭제되지 않은 Area를 ID 오름차순으로 조회한다.
     *
     * @return 목록 화면에 필요한 Area 요약 목록
     */
    @GetMapping
    public List<AreaSummaryResponse> findAll() {
        return areaService.findAll();
    }

    /**
     * 논리 삭제되지 않은 Area 한 건을 조회한다.
     *
     * @param areaId 조회할 Area ID
     * @return 생성·수정 시각을 포함한 상세 응답
     */
    @GetMapping("/{areaId}")
    public AreaDetailResponse findById(@PathVariable Long areaId) {
        return areaService.findById(areaId);
    }

    /**
     * Area의 모든 입력 필드를 PUT 방식으로 교체한다.
     *
     * @param areaId 수정할 Area ID
     * @param request 교체할 전체 Area 값
     * @return 수정된 Area 상세 응답
     */
    @PutMapping("/{areaId}")
    public AreaDetailResponse update(
            @PathVariable Long areaId,
            @Valid @RequestBody AreaRequest request
    ) {
        return areaService.update(areaId, request);
    }

    /**
     * Area를 논리 삭제한다.
     *
     * @param areaId 삭제할 Area ID
     * @return {@code 204 No Content}
     */
    @DeleteMapping("/{areaId}")
    public ResponseEntity<Void> delete(@PathVariable Long areaId) {
        areaService.delete(areaId);
        return ResponseEntity.noContent().build();
    }
}
