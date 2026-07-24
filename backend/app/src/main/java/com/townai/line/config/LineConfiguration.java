package com.townai.line.config;

import com.google.cloud.tasks.v2.CloudTasksClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

/**
 * LINE Messaging API 연동에 필요한 설정 값을 Bean으로 등록한다.
 */
@Configuration
@EnableConfigurationProperties({
        LineProperties.class,
        LineMessagingProperties.class,
        LineTaskProperties.class
})
public class LineConfiguration {

    /**
     * LINE 설정 등록 구성을 생성한다.
     */
    public LineConfiguration() {
    }

    /**
     * Production에서 Cloud Tasks Task를 생성할 Client를 등록한다.
     *
     * <p>Local 실행에서는 Google Cloud 인증을 요구하지 않도록
     * {@code town-ai.line.event-dispatcher=cloud-tasks}일 때만 생성한다.</p>
     *
     * @return Application Default Credentials를 사용하는 Cloud Tasks Client
     * @throws IOException Client 초기화에 실패한 경우
     */
    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(
            prefix = "town-ai.line",
            name = "event-dispatcher",
            havingValue = "cloud-tasks"
    )
    CloudTasksClient cloudTasksClient() throws IOException {
        return CloudTasksClient.create();
    }
}
