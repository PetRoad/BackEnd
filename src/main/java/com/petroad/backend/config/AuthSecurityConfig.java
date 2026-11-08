package com.petroad.backend.config;

import com.petroad.backend.security.LoginThrottleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.autoconfigure.validation.ValidationConfigurationCustomizer;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class AuthSecurityConfig {
    @Bean public Clock authClock() { return Clock.systemUTC(); }
    @Bean public ValidationConfigurationCustomizer seoulValidationClock() {
        return configuration -> configuration.clockProvider(() -> Clock.system(ZoneId.of("Asia/Seoul")));
    }
}
