package com.petroad.backend.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "jwt.secret=login-api-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=login_api_test", "spring.flyway.default-schema=login_api_test",
        "spring.jpa.properties.hibernate.default_schema=login_api_test",
        "spring.datasource.hikari.schema=login_api_test",
        "security.login.max-failures=5", "security.login.failure-window=15m",
        "security.login.account-max-failures=50", "security.login.account-failure-window=1h",
        "security.login.ip-max-attempts=30", "security.login.ip-window=1m"
})
@AutoConfigureMockMvc
class LoginThrottleApiTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;
    private static final String PASSWORD = "Password123!";
    private final Instant now = Instant.parse("2026-11-27T00:00:00Z");
    private User owner;

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        jdbc.update("DELETE FROM rate_limit_hits");
        owner = users.save(new User(UUID.randomUUID() + "@example.com", passwords.encode(PASSWORD)));
    }

    private MockHttpServletRequestBuilder login(String email, String password, String ip) throws Exception {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("email", email, "password", password)))
                .with(request -> { request.setRemoteAddr(ip); return request; });
    }

    @Test void attackFromOneIpCannotLockOutTheOwnerOnAnotherIp() throws Exception {
        for (int n=0;n<5;n++) {
            mvc.perform(login(owner.getEmail(),"wrong-password","192.0.2.1"))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(owner.getEmail(),PASSWORD,"192.0.2.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"))
                .andExpect(jsonPath("$.message").isString());
        String json = mvc.perform(login(owner.getEmail(),PASSWORD,"192.0.2.2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.userId").value(owner.getId()))
                .andReturn().getResponse().getContentAsString();
        String token = mapper.readTree(json).get("accessToken").asText();
        mvc.perform(get("/api/me").header("Authorization","Bearer " + token)).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_session WHERE user_id=?",Long.class,owner.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM rate_limit_hits WHERE bucket='login-account'",Long.class)).isEqualTo(5);
    }

    @Test void distributedFailuresReachTheAccountWideLimitAndExpireAfterAnHour() throws Exception {
        for (int n=0;n<50;n++) {
            mvc.perform(login(owner.getEmail(),"wrong-password","192.0.2."+(n/5+1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(owner.getEmail(),PASSWORD,"198.51.100.1"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","3600"));
        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(30)));
        mvc.perform(login(owner.getEmail(),PASSWORD,"198.51.100.2"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","1800"));
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(1)));
        mvc.perform(login(owner.getEmail(),PASSWORD,"198.51.100.2")).andExpect(status().isOk());
    }

    @Test void ipv6SuffixChangesCannotBypassThePairLimit() throws Exception {
        for (int n=0;n<5;n++) {
            mvc.perform(login(owner.getEmail(),"wrong-password","2001:db8:1:2::"+(n+1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(owner.getEmail(),PASSWORD,"2001:db8:1:2:ffff:ffff:ffff:ffff"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","900"));
        mvc.perform(login(owner.getEmail(),PASSWORD,"2001:db8:1:3::1")).andExpect(status().isOk());
    }

    @Test void forgedHeadersAndIpv6AddressRotationCannotBypassTheIpLimit() throws Exception {
        for (int n=0;n<30;n++) {
            mvc.perform(login("unknown"+n+"@example.com","wrong-password","2001:db8:1:2::"+(n+1))
                            .header("CF-Connecting-IP","192.0.2."+(n+1)).header("X-Forwarded-For","192.0.2."+(n+1)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(login(owner.getEmail(),PASSWORD,"2001:db8:1:2::ffff"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","60"));
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        mvc.perform(login(owner.getEmail(),PASSWORD,"2001:db8:1:2::ffff")).andExpect(status().isOk());
    }
}
