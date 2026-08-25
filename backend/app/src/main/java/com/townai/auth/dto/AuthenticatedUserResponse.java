package com.townai.auth.dto;

import com.townai.auth.security.AuthenticatedWebUser;

/**
 * 현재 Web 관리 화면 사용자의 공개 가능한 인증 정보이다.
 *
 * @param uid Firebase User ID
 * @param email Firebase 사용자 이메일
 * @param name Firebase 사용자 표시 이름
 */
public record AuthenticatedUserResponse(String uid, String email, String name) {

    /**
     * Security Principal을 API 응답으로 변환한다.
     *
     * @param user 검증된 Firebase 사용자
     * @return 사용자 응답 DTO
     */
    public static AuthenticatedUserResponse from(AuthenticatedWebUser user) {
        return new AuthenticatedUserResponse(user.uid(), user.email(), user.name());
    }
}
