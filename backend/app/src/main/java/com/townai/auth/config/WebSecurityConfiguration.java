package com.townai.auth.config;

import com.townai.auth.security.FirebaseAuthenticationFilter;
import com.townai.auth.security.WebSecurityErrorWriter;
import com.townai.common.error.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Web 관리 API와 기존 LINE·Cloud Tasks Endpoint의 인증 경계를 정의한다.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(WebAuthProperties.class)
public class WebSecurityConfiguration {

    /**
     * Web 관리 API Security 구성을 생성한다.
     */
    public WebSecurityConfiguration() {
    }

    /**
     * Stateless Bearer 인증과 공개 예외 경로를 구성한다.
     *
     * <p>LINE에서 직접 여는 공개 Report Endpoint는 별도의 만료 HMAC 서명을 검증한다.
     * 기존 Web Report Endpoint는 Firebase 인증을 요구한다.</p>
     *
     * @param http Spring Security HTTP 구성기
     * @param properties Web 인증 활성화 설정
     * @param filterProvider 조건부 Firebase 인증 필터
     * @param errorWriter 공통 인증 오류 응답 작성기
     * @return 구성된 Security Filter Chain
     * @throws Exception Security Filter Chain 생성 실패
     */
    @Bean
    SecurityFilterChain webSecurityFilterChain(
            HttpSecurity http,
            WebAuthProperties properties,
            ObjectProvider<FirebaseAuthenticationFilter> filterProvider,
            WebSecurityErrorWriter errorWriter
    ) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable());

        if (properties.enabled()) {
            FirebaseAuthenticationFilter filter = filterProvider.getObject();
            http
                    .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class)
                    .exceptionHandling(exceptions -> exceptions
                            .authenticationEntryPoint((request, response, exception) ->
                                    errorWriter.write(
                                            request,
                                            response,
                                            ErrorCode.AUTHENTICATION_REQUIRED
                                    ))
                            .accessDeniedHandler((request, response, exception) ->
                                    errorWriter.write(
                                            request,
                                            response,
                                            ErrorCode.WEB_ACCESS_DENIED
                                    )))
                    .authorizeHttpRequests(authorize -> authorize
                            .requestMatchers(
                                    "/actuator/health/**",
                                    "/api/line/webhook",
                                    "/internal/tasks/line-events/**",
                                    "/api/public/reports/*/content",
                                    "/api/public/reports/*/download"
                            ).permitAll()
                            .requestMatchers("/api/**").authenticated()
                            .anyRequest().permitAll());
        } else {
            http.authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());
        }
        return http.build();
    }
}
