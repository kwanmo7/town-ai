package com.townai.auth.controller;

import com.townai.auth.dto.AuthenticatedUserResponse;
import com.townai.auth.security.AuthenticatedWebUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Frontend가 Firebase 로그인과 Backend 권한 검증 완료를 확인하는 API이다.
 */
@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "town-ai.web-auth", name = "enabled", havingValue = "true")
public class AuthController {

    /**
     * 현재 사용자 조회 Controller를 생성한다.
     */
    public AuthController() {
    }

    /**
     * 현재 인증된 단일 관리자를 반환한다.
     *
     * @param user Firebase 인증 필터가 설정한 사용자 Principal
     * @return 현재 관리자 정보
     */
    @GetMapping("/me")
    public AuthenticatedUserResponse me(
            @AuthenticationPrincipal AuthenticatedWebUser user
    ) {
        return AuthenticatedUserResponse.from(user);
    }
}
