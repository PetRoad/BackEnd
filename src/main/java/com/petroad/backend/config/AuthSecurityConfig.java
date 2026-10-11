package com.petroad.backend.config;

import com.petroad.backend.security.LoginThrottleProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(LoginThrottleProperties.class)
public class AuthSecurityConfig {}
