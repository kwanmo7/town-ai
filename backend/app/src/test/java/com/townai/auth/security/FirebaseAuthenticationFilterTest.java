package com.townai.auth.security;

import com.townai.auth.config.WebAuthProperties;
import com.townai.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FirebaseAuthenticationFilterTest {

    private FirebaseIdTokenVerifier tokenVerifier;
    private WebSecurityErrorWriter errorWriter;
    private FirebaseAuthenticationFilter filter;

    @BeforeEach
    void setUp() {
        tokenVerifier = mock(FirebaseIdTokenVerifier.class);
        errorWriter = mock(WebSecurityErrorWriter.class);
        filter = new FirebaseAuthenticationFilter(
                tokenVerifier,
                new WebAuthProperties(true, "town-ai", "allowed-uid"),
                errorWriter
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization Header가 없으면 Security Chain의 미인증 처리에 맡긴다")
    void delegatesMissingTokenToSecurityChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/areas");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenVerifier, never()).verify(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    @DisplayName("유효한 허용 UID를 Spring Security 인증으로 등록한다")
    void authenticatesAllowedFirebaseUser() throws Exception {
        MockHttpServletRequest request = requestWithBearer("valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        AuthenticatedWebUser user = new AuthenticatedWebUser(
                "allowed-uid",
                "owner@example.com",
                "Owner"
        );
        when(tokenVerifier.verify("valid-token")).thenReturn(user);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(user);
    }

    @Test
    @DisplayName("허용되지 않은 UID는 403 오류로 차단한다")
    void rejectsFirebaseUserWithDifferentUid() throws Exception {
        MockHttpServletRequest request = requestWithBearer("other-user-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(tokenVerifier.verify("other-user-token")).thenReturn(
                new AuthenticatedWebUser("other-uid", "other@example.com", "Other")
        );

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, ErrorCode.WEB_ACCESS_DENIED);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("검증에 실패한 Firebase Token은 401 오류로 차단한다")
    void rejectsInvalidFirebaseToken() throws Exception {
        MockHttpServletRequest request = requestWithBearer("invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(tokenVerifier.verify("invalid-token")).thenThrow(
                new InvalidFirebaseTokenException(new IllegalArgumentException())
        );

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, ErrorCode.INVALID_FIREBASE_TOKEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Bearer 형식이 아닌 Authorization Header를 거부한다")
    void rejectsUnsupportedAuthorizationScheme() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/areas");
        request.addHeader("Authorization", "Basic credentials");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(errorWriter).write(request, response, ErrorCode.INVALID_FIREBASE_TOKEN);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("Cloud Tasks OIDC Bearer Token은 Firebase 검증 대상에서 제외한다")
    void preservesCloudTasksOidcAuthenticationBoundary() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "POST",
                "/internal/tasks/line-events/event-1"
        );
        request.addHeader("Authorization", "Bearer cloud-tasks-oidc-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenVerifier, never()).verify(org.mockito.ArgumentMatchers.anyString());
        verify(errorWriter, never()).write(
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    @DisplayName("서명 공개 Report Endpoint는 Firebase 검증 대상에서 제외한다")
    void preservesSignedReportLinkBoundary() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/api/public/reports/10/content"
        );
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verify(tokenVerifier, never()).verify(org.mockito.ArgumentMatchers.anyString());
    }

    private MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/areas");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
