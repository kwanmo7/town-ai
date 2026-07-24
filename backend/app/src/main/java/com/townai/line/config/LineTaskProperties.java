package com.townai.line.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LINE Webhook 이벤트를 Local 또는 Cloud Tasks 처리기로 전달하기 위한 설정이다.
 *
 * @param eventDispatcher 사용할 Dispatcher 유형. {@code local} 또는
 *                        {@code cloud-tasks}
 * @param localTaskTargetUrl Local Backend의 내부 Task Endpoint 기본 URL
 * @param cloudTasksProjectId Cloud Tasks Queue가 속한 GCP Project ID
 * @param cloudTasksLocation Cloud Tasks Queue Region
 * @param cloudTasksQueue Cloud Tasks Queue 이름
 * @param cloudTasksTargetUrl Cloud Run 내부 Task Endpoint 기본 URL
 * @param cloudTasksOidcAudience Cloud Tasks가 발급할 OIDC Token Audience
 * @param cloudTasksServiceAccount OIDC Token에 사용할 Service Account Email
 */
@ConfigurationProperties(prefix = "town-ai.line")
public record LineTaskProperties(
        String eventDispatcher,
        String localTaskTargetUrl,
        String cloudTasksProjectId,
        String cloudTasksLocation,
        String cloudTasksQueue,
        String cloudTasksTargetUrl,
        String cloudTasksOidcAudience,
        String cloudTasksServiceAccount
) {
}
