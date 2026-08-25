package com.townai.auth.security;

/**
 * 검증된 Firebase ID Token에서 관리 화면에 필요한 사용자 정보만 보존한다.
 *
 * @param uid Firebase User ID
 * @param email Firebase 사용자 이메일
 * @param name Firebase 사용자 표시 이름
 */
public record AuthenticatedWebUser(String uid, String email, String name) {
}
