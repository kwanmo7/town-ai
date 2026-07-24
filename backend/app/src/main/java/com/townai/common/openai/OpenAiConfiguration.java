package com.townai.common.openai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAI 설정 값을 타입 안전한 Bean으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OpenAiProperties.class)
public class OpenAiConfiguration {

    /**
     * OpenAI 설정 등록 구성을 생성한다.
     */
    public OpenAiConfiguration() {
    }
}
