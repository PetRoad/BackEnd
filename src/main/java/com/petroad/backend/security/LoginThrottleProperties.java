package com.petroad.backend.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.time.Duration;
import java.util.List;

@ConfigurationProperties("security.login")
public record LoginThrottleProperties(int maxFailures, Duration failureWindow, Duration lockDuration,
                                      int ipMaxAttempts, Duration ipWindow, int maxTrackedEntries,
                                      List<String> trustedProxyAddresses) {
    public LoginThrottleProperties {
        if (maxFailures < 1 || ipMaxAttempts < 1 || maxTrackedEntries < 1
                || !positive(failureWindow) || !positive(lockDuration) || !positive(ipWindow)) {
            throw new IllegalArgumentException("로그인 제한 횟수와 시간은 양수여야 합니다.");
        }
        trustedProxyAddresses = trustedProxyAddresses == null ? List.of() : List.copyOf(trustedProxyAddresses);
    }

    private static boolean positive(Duration duration) {
        return duration != null && duration.compareTo(Duration.ofSeconds(1)) >= 0;
    }
}
