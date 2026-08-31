package com.townai.line.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 같은 Local Backend Port에서 전달되는 Task 요청을 허용한다.
 *
 * <p>Local 실행은 외부에 공개하지 않는 개발 환경이라는 전제에서 OIDC를 요구하지
 * 않는다. Production에서는 반드시 {@code cloud-tasks} Dispatcher를 사용한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.line",
        name = "event-dispatcher",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalLineTaskRequestAuthenticator
        implements LineTaskRequestAuthenticator {

    /**
     * Local 요청은 별도 Token 없이 허용한다.
     *
     * @param authorizationHeader 사용하지 않는 Authorization Header
     */
    @Override
    public void authenticate(String authorizationHeader) {
        // Local Backend 내부 호출은 OIDC 인증 대상이 아니다.
    }
}
