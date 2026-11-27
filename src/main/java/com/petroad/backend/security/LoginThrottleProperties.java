package com.petroad.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("security.login")
public record LoginThrottleProperties(int maxFailures, Duration failureWindow,
                                      int accountMaxFailures, Duration accountFailureWindow,
                                      int ipMaxAttempts, Duration ipWindow,
                                      List<String> trustedProxyAddresses) {
    public LoginThrottleProperties {
        if (maxFailures < 1 || accountMaxFailures < 1 || ipMaxAttempts < 1
                || !supported(failureWindow) || !supported(accountFailureWindow) || !supported(ipWindow)) {
            throw new IllegalArgumentException("로그인 제한 횟수는 양수, 시간은 1초 이상 1일 이하여야 합니다.");
        }
        trustedProxyAddresses = trustedProxyAddresses == null ? List.of() : List.copyOf(trustedProxyAddresses);
    }

    private static boolean supported(Duration duration) {
        // 공용 RateLimiter가 1일 지난 기록을 정리하므로 조회 창도 그 범위를 넘지 않는다.
        return duration != null && duration.compareTo(Duration.ofSeconds(1)) >= 0
                && duration.compareTo(Duration.ofDays(1)) <= 0;
    }
}
