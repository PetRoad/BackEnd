package com.petroad.backend.security;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class LoginThrottlePropertiesTest {
    @Test void validatesCountsAndTheRetentionCompatibleWindows() {
        for (int max : new int[]{0,-1}) {
            assertThatThrownBy(() -> new LoginThrottleProperties(max,Duration.ofMinutes(15),50,Duration.ofHours(1),30,Duration.ofMinutes(1),List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LoginThrottleProperties(5,Duration.ofMinutes(15),max,Duration.ofHours(1),30,Duration.ofMinutes(1),List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LoginThrottleProperties(5,Duration.ofMinutes(15),50,Duration.ofHours(1),max,Duration.ofMinutes(1),List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        for (Duration window : Arrays.asList(null,Duration.ZERO,Duration.ofMillis(999),Duration.ofDays(1).plusNanos(1))) {
            assertThatThrownBy(() -> new LoginThrottleProperties(5,window,50,Duration.ofHours(1),30,Duration.ofMinutes(1),List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LoginThrottleProperties(5,Duration.ofMinutes(15),50,window,30,Duration.ofMinutes(1),List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new LoginThrottleProperties(5,Duration.ofMinutes(15),50,Duration.ofHours(1),30,window,List.of()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatCode(() -> new LoginThrottleProperties(5,Duration.ofSeconds(1),50,Duration.ofDays(1),30,Duration.ofMinutes(1),null))
                .doesNotThrowAnyException();
    }
}
