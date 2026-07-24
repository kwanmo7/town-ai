package com.townai.line.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LINE Messaging API 인증과 개인 사용자 제한에 사용하는 설정이다.
 *
 * <p>값이 비어 있어도 애플리케이션은 시작할 수 있지만, 서명 검증은 항상 실패하고
 * 허용되는 사용자 이벤트도 없으므로 안전하게 비활성화된다. 실제 Webhook을 연결하는
 * 환경에서는 두 값을 Secret으로 주입한다.</p>
 *
 * @param channelSecret Webhook 서명 검증에 사용하는 Channel Secret
 * @param allowedUserId V1에서 처리할 단일 LINE User ID
 */
@ConfigurationProperties(prefix = "town-ai.line")
public record LineProperties(
        String channelSecret,
        String allowedUserId
) {
}
