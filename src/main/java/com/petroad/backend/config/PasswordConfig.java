package com.petroad.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.petroad.backend.security.BoundedBCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class PasswordConfig {
    @Bean public PasswordEncoder passwordEncoder() { return new BoundedBCryptPasswordEncoder(); }
}
