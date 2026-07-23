package com.townai.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * 날짜와 시각 계산에 사용하는 공통 Bean을 구성한다.
 *
 * <p>저장 시각은 UTC {@link Clock}을 사용하고, 자연어의 '오늘'과 같은 날짜 표현은
 * 사용자의 생활권 {@link ZoneId}를 사용한다. 두 값을 주입 가능하게 유지해 테스트에서
 * 고정 시각과 고정 시간대로 교체할 수 있다.</p>
 */
@Configuration
public class TimeConfig {

    /**
     * 서버와 DB에 기록할 현재 시각의 기준을 제공한다.
     *
     * @return UTC 시스템 시계를 사용하는 Clock
     */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * 상대적인 방문 날짜 표현을 사용자의 생활권 날짜로 해석한다.
     *
     * @param zoneId IANA Time Zone ID. 설정하지 않으면 {@code Asia/Tokyo}
     * @return 자연어 날짜 해석에 사용할 시간대
     */
    @Bean
    ZoneId userTimeZone(
            @Value("${town-ai.user-time-zone:Asia/Tokyo}") String zoneId
    ) {
        return ZoneId.of(zoneId);
    }
}
