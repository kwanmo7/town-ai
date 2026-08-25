package com.townai.auth.security;

import com.townai.auth.config.WebAuthProperties;
import com.townai.common.error.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Bearer로 전달된 Firebase ID Token을 검증해 Spring Security 인증으로 변환한다.
 */
@Component
@ConditionalOnProperty(prefix = "town-ai.web-auth", name = "enabled", havingValue = "true")
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthenticationFilter.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Pattern SIGNED_REPORT_PATH = Pattern.compile(
            "^/api/public/reports/[^/]+/(content|download)$"
    );

    private final FirebaseIdTokenVerifier tokenVerifier;
    private final WebAuthProperties properties;
    private final WebSecurityErrorWriter errorWriter;

    /**
     * Firebase 인증 필터를 생성한다.
     *
     * @param tokenVerifier Firebase ID Token 검증기
     * @param properties 단일 사용자 허용 설정
     * @param errorWriter 공통 인증 오류 응답 작성기
     */
    public FirebaseAuthenticationFilter(
            FirebaseIdTokenVerifier tokenVerifier,
            WebAuthProperties properties,
            WebSecurityErrorWriter errorWriter
    ) {
        this.tokenVerifier = tokenVerifier;
        this.properties = properties;
        this.errorWriter = errorWriter;
    }

    /**
     * Firebase Web 인증과 별도의 신뢰 경계를 사용하는 요청을 제외한다.
     *
     * <p>특히 Cloud Tasks의 OIDC Bearer Token을 Firebase ID Token으로 오인하지
     * 않도록 내부 Task Endpoint를 반드시 건너뛴다.</p>
     */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/health")
                || path.equals("/api/line/webhook")
                || path.startsWith("/internal/tasks/line-events/")
                || ("GET".equals(request.getMethod())
                && SIGNED_REPORT_PATH.matcher(path).matches());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!authorization.startsWith(BEARER_PREFIX)) {
            errorWriter.write(request, response, ErrorCode.INVALID_FIREBASE_TOKEN);
            return;
        }

        String idToken = authorization.substring(BEARER_PREFIX.length()).strip();
        if (idToken.isEmpty()) {
            errorWriter.write(request, response, ErrorCode.INVALID_FIREBASE_TOKEN);
            return;
        }

        try {
            AuthenticatedWebUser user = tokenVerifier.verify(idToken);
            if (!properties.allowedUid().equals(user.uid())) {
                log.warn("Denied Firebase user. uid={}", user.uid());
                errorWriter.write(request, response, ErrorCode.WEB_ACCESS_DENIED);
                return;
            }
            UsernamePasswordAuthenticationToken authentication =
                    UsernamePasswordAuthenticationToken.authenticated(
                            user,
                            idToken,
                            List.of()
                    );
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (InvalidFirebaseTokenException exception) {
            log.warn("Rejected invalid Firebase ID token.");
            SecurityContextHolder.clearContext();
            errorWriter.write(request, response, ErrorCode.INVALID_FIREBASE_TOKEN);
        }
    }
}
