package com.petroad.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=signup-limit-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=signup_limit_test", "spring.flyway.default-schema=signup_limit_test",
        "spring.jpa.properties.hibernate.default_schema=signup_limit_test",
        "spring.datasource.hikari.schema=signup_limit_test"
})
@AutoConfigureMockMvc
class SignupRateLimitTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;
    private final Instant now = Instant.parse("2026-11-26T00:00:00Z");

    @BeforeEach void reset() {
        when(clock.instant()).thenReturn(now);
        jdbc.update("delete from rate_limit_hits");
    }

    private static String newEmail() { return UUID.randomUUID() + "@example.com"; }

    private int signup(String email, String ip) throws Exception {
        return mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("email", email, "password", "Password123!")))
                        .with(request -> { request.setRemoteAddr(ip); return request; }))
                .andReturn().getResponse().getStatus();
    }

    @Test void eleventhSignupFromOneIpIsBlockedUntilTheHourPasses() throws Exception {
        for (int n = 0; n < 10; n++) assertThat(signup(newEmail(), "192.0.2.1")).isEqualTo(200);
        assertThat(signup(newEmail(), "192.0.2.1")).isEqualTo(429);
        assertThat(signup(newEmail(), "192.0.2.2")).isEqualTo(200);
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(1)));
        assertThat(signup(newEmail(), "192.0.2.1")).isEqualTo(200);
    }

    @Test void failedDuplicateSignupsAreAlsoCounted() throws Exception {
        String email = newEmail();
        assertThat(signup(email, "192.0.2.3")).isEqualTo(200);
        for (int n = 0; n < 9; n++) assertThat(signup(email, "192.0.2.3")).isEqualTo(400);
        assertThat(signup(newEmail(), "192.0.2.3")).isEqualTo(429);
    }

    @Test void ipv6AddressesInTheSame64ShareOneLimit() throws Exception {
        for (int n = 1; n <= 10; n++) assertThat(signup(newEmail(), "2001:db8:1:2::" + n)).isEqualTo(200);
        assertThat(signup(newEmail(), "2001:db8:1:2:ffff::1")).isEqualTo(429);
        assertThat(signup(newEmail(), "2001:db8:1:3::1")).isEqualTo(200);
    }

    @Test void parallelSignupsCannotExceedTheLimit() throws Exception {
        int requests = 15;
        var pool = Executors.newFixedThreadPool(requests);
        try {
            var start = new CountDownLatch(1);
            List<Future<Integer>> results = new ArrayList<>();
            for (int n = 0; n < requests; n++) {
                results.add(pool.submit(() -> {
                    start.await();
                    return signup(newEmail(), "192.0.2.4");
                }));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (var result : results) statuses.add(result.get(60, TimeUnit.SECONDS));
            assertThat(statuses).filteredOn(code -> code == 200).hasSize(10);
            assertThat(statuses).filteredOn(code -> code == 429).hasSize(5);
        } finally {
            pool.shutdownNow();
        }
    }
}
