package com.townai.line.dispatcher;

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.cloud.tasks.v2.TaskName;
import com.townai.line.config.LineTaskProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Production에서 LINE 이벤트를 Cloud Tasks HTTP Target으로 전달한다.
 *
 * <p>Webhook Event ID에서 만든 결정적 Task ID를 명시한다. 동일 Task가 이미
 * 존재한다는 응답은 이전 전달이 안전하게 수락된 것으로 판단해 성공 처리한다.
 * Cloud Run 호출 인증에는 전용 Service Account의 OIDC Token을 사용한다.</p>
 */
@Component
@ConditionalOnProperty(
        prefix = "town-ai.line",
        name = "event-dispatcher",
        havingValue = "cloud-tasks"
)
public class CloudTasksLineEventDispatcher implements LineEventDispatcher {

    private final CloudTasksClient cloudTasksClient;
    private final LineTaskIdFactory taskIdFactory;
    private final String projectId;
    private final String location;
    private final String queue;
    private final String targetUrl;
    private final String oidcAudience;
    private final String serviceAccount;

    /**
     * Cloud Tasks Dispatcher를 생성하고 필수 Production 설정을 검증한다.
     *
     * @param cloudTasksClient Application Default Credentials를 사용하는 Client
     * @param taskIdFactory 결정적 Task ID Factory
     * @param properties Cloud Tasks Queue와 OIDC 설정
     */
    public CloudTasksLineEventDispatcher(
            CloudTasksClient cloudTasksClient,
            LineTaskIdFactory taskIdFactory,
            LineTaskProperties properties
    ) {
        this.cloudTasksClient = cloudTasksClient;
        this.taskIdFactory = taskIdFactory;
        this.projectId = requireNonBlank(
                properties.cloudTasksProjectId(),
                "GCP project ID"
        );
        this.location = requireNonBlank(
                properties.cloudTasksLocation(),
                "Cloud Tasks location"
        );
        this.queue = requireNonBlank(
                properties.cloudTasksQueue(),
                "Cloud Tasks queue"
        );
        this.targetUrl = requireNonBlank(
                properties.cloudTasksTargetUrl(),
                "Cloud Tasks target URL"
        );
        this.oidcAudience = requireNonBlank(
                properties.cloudTasksOidcAudience(),
                "Cloud Tasks OIDC audience"
        );
        this.serviceAccount = requireNonBlank(
                properties.cloudTasksServiceAccount(),
                "Cloud Tasks service account"
        );
    }

    /**
     * OIDC 인증이 포함된 HTTP POST Task를 Queue에 생성한다.
     *
     * @param webhookEventId DB에 저장된 LINE Webhook Event ID
     */
    @Override
    public void dispatch(String webhookEventId) {
        String taskId = taskIdFactory.create(webhookEventId);
        String taskName = TaskName.of(
                projectId,
                location,
                queue,
                taskId
        ).toString();
        String eventUrl = UriComponentsBuilder.fromUriString(targetUrl)
                .pathSegment(webhookEventId)
                .build()
                .encode()
                .toUriString();

        OidcToken oidcToken = OidcToken.newBuilder()
                .setServiceAccountEmail(serviceAccount)
                .setAudience(oidcAudience)
                .build();
        HttpRequest httpRequest = HttpRequest.newBuilder()
                .setHttpMethod(HttpMethod.POST)
                .setUrl(eventUrl)
                .setOidcToken(oidcToken)
                .build();
        Task task = Task.newBuilder()
                .setName(taskName)
                .setHttpRequest(httpRequest)
                .build();
        CreateTaskRequest request = CreateTaskRequest.newBuilder()
                .setParent(QueueName.of(
                        projectId,
                        location,
                        queue
                ).toString())
                .setTask(task)
                .build();

        try {
            cloudTasksClient.createTask(request);
        } catch (AlreadyExistsException exception) {
            // 결정적 Task ID가 이미 존재하면 이전 전달이 성공한 것이다.
        } catch (RuntimeException exception) {
            throw new LineEventDispatchException(
                    "Cloud Tasks LINE event dispatch failed.",
                    exception
            );
        }
    }

    private String requireNonBlank(String value, String settingName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(settingName + " is required.");
        }
        return value;
    }
}
