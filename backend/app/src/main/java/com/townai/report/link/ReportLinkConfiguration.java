package com.townai.report.link;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 만료 Report 링크 설정을 애플리케이션 Bean으로 등록한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReportLinkProperties.class)
public class ReportLinkConfiguration {

}
