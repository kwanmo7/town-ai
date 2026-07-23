package com.townai.visit.controller;

import com.townai.visit.dto.VisitDraftRequest;
import com.townai.visit.dto.VisitDraftResponse;
import com.townai.visit.service.VisitDraftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 자연어 방문 평가를 Visit 등록 전 확인용 초안으로 변환하는 REST Controller이다.
 *
 * <p>이 API는 파싱 결과만 반환하며 Visit을 저장하지 않는다. 사용자가 초안을 확인하고
 * 필요한 값을 보완한 뒤 별도의 Visit 생성 API를 호출해야 한다.</p>
 */
@RestController
@RequestMapping("/api/visit-drafts")
public class VisitDraftController {

    private final VisitDraftService visitDraftService;

    /**
     * Visit Draft Controller를 생성한다.
     *
     * @param visitDraftService 자연어 Visit 초안을 생성할 Service
     */
    public VisitDraftController(VisitDraftService visitDraftService) {
        this.visitDraftService = visitDraftService;
    }

    /**
     * 자연어에서 Area, 방문일, 점수와 메모 후보를 추출한다.
     *
     * @param request 사용자가 작성한 자연어 방문 평가
     * @return 확인 및 보완이 필요한 Visit 초안
     */
    @PostMapping
    public VisitDraftResponse create(
            @Valid @RequestBody VisitDraftRequest request
    ) {
        return visitDraftService.create(request);
    }
}
