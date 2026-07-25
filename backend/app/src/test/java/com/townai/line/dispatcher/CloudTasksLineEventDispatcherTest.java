package com.townai.line.dispatcher;

import com.google.api.gax.rpc.AlreadyExistsException;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.CreateTaskRequest;
import com.townai.line.config.LineTaskProperties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudTasksLineEventDispatcherTest {

    private final CloudTasksClient client = mock(CloudTasksClient.class);
    private final LineTaskIdFactory taskIdFactory =
            new LineTaskIdFactory();

    @Test
    void createsNamedHttpTaskWithOidcToken() {
        CloudTasksLineEventDispatcher dispatcher = dispatcher();

        dispatcher.dispatch("event-1");

        ArgumentCaptor<CreateTaskRequest> captor =
                ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(client).createTask(captor.capture());
        CreateTaskRequest request = captor.getValue();

        assertEquals(
                "projects/town-ai/locations/asia-northeast1/queues/line-events",
                request.getParent()
        );
        assertEquals(
                "projects/town-ai/locations/asia-northeast1/queues/"
                        + "line-events/tasks/"
                        + taskIdFactory.create("event-1"),
                request.getTask().getName()
        );
        assertEquals(
                "https://town-ai.run.app/internal/tasks/line-events/event-1",
                request.getTask().getHttpRequest().getUrl()
        );
        assertEquals(
                "line-task@town-ai.iam.gserviceaccount.com",
                request.getTask()
                        .getHttpRequest()
                        .getOidcToken()
                        .getServiceAccountEmail()
        );
        assertEquals(
                "https://town-ai.run.app",
                request.getTask()
                        .getHttpRequest()
                        .getOidcToken()
                        .getAudience()
        );
        assertEquals(
                300,
                request.getTask().getDispatchDeadline().getSeconds()
        );
    }

    @Test
    void treatsAlreadyExistingTaskAsSuccessfulDispatch() {
        AlreadyExistsException exception =
                mock(AlreadyExistsException.class);
        when(client.createTask(any(CreateTaskRequest.class)))
                .thenThrow(exception);
        CloudTasksLineEventDispatcher dispatcher = dispatcher();

        assertDoesNotThrow(() -> dispatcher.dispatch("event-1"));
    }

    @Test
    void wrapsUnexpectedCloudTasksFailure() {
        when(client.createTask(any(CreateTaskRequest.class)))
                .thenThrow(new IllegalStateException("unavailable"));
        CloudTasksLineEventDispatcher dispatcher = dispatcher();

        assertThrows(
                LineEventDispatchException.class,
                () -> dispatcher.dispatch("event-1")
        );
    }

    private CloudTasksLineEventDispatcher dispatcher() {
        return new CloudTasksLineEventDispatcher(
                client,
                taskIdFactory,
                new LineTaskProperties(
                        "cloud-tasks",
                        "",
                        "town-ai",
                        "asia-northeast1",
                        "line-events",
                        "https://town-ai.run.app/internal/tasks/line-events",
                        "https://town-ai.run.app",
                        "line-task@town-ai.iam.gserviceaccount.com"
                )
        );
    }
}
