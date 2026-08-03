package com.townai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Town AI Backend 애플리케이션의 진입점이다.
 *
 * <p>이 클래스가 위치한 {@code com.townai} 패키지를 기준으로 Spring Component와
 * JPA Repository를 탐색한다.</p>
 */
@SpringBootApplication(proxyBeanMethods = false)
public class App {

    /**
     * Spring이 애플리케이션 구성 클래스를 생성할 때 사용하는 기본 생성자다.
     */
    public App() {
    }

    /**
     * Spring Boot 애플리케이션을 시작한다.
     *
     * @param args 실행 시 전달된 명령행 인자
     */
    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }
}
