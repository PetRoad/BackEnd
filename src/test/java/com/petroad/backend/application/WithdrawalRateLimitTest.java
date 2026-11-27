package com.petroad.backend.application;

import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import com.petroad.backend.security.LoginRateLimitException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

// 테스트에 @Transactional을 붙이지 않는다. 실패 기록이 실제로 커밋되는지 확인해야 한다.
@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=withdrawal-limit-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=withdrawal_limit_test", "spring.flyway.default-schema=withdrawal_limit_test",
        "spring.jpa.properties.hibernate.default_schema=withdrawal_limit_test",
        "spring.datasource.hikari.schema=withdrawal_limit_test"
})
class WithdrawalRateLimitTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }

    @Autowired WithdrawalService service;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    private static final String PASSWORD = "Password123!";
    private final Instant now = Instant.parse("2026-11-26T00:00:00Z");

    @BeforeEach void time() { when(clock.instant()).thenReturn(now); }

    private Long newUser() {
        return users.save(new User(UUID.randomUUID() + "@example.com", encoder.encode(PASSWORD))).getId();
    }

    private int failures(Long userId) {
        return jdbc.queryForObject("SELECT count(*) FROM rate_limit_hits WHERE bucket = 'withdraw' AND key = ?",
                Integer.class, String.valueOf(userId));
    }

    @Test void blocksTheSixthAttemptAndReleasesAfterFifteenMinutes() {
        Long userId = newUser();
        for (int i = 1; i <= 5; i++) {
            assertThatThrownBy(() -> service.withdraw(userId, "wrong-password"))
                    .isInstanceOf(WithdrawalService.WrongPasswordException.class);
            assertThat(failures(userId)).isEqualTo(i);
        }

        // 맞는 비밀번호여도 제한 중에는 거절한다.
        assertThatThrownBy(() -> service.withdraw(userId, PASSWORD))
                .isInstanceOfSatisfying(LoginRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900));
        assertThat(users.existsById(userId)).isTrue();
        assertThat(failures(userId)).isEqualTo(5);

        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(15)));
        service.withdraw(userId, PASSWORD);
        assertThat(users.existsById(userId)).isFalse();
    }

    @Test void parallelWrongPasswordsCannotExceedTheLimit() throws Exception {
        Long userId = newUser();
        int requests = 8; // 기본 DB 연결 수(10)보다 적게 둔다.
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Class<?>> call = () -> {
                start.await();
                try {
                    service.withdraw(userId, "wrong-password");
                    return Void.class;
                } catch (RuntimeException e) {
                    return e.getClass();
                }
            };
            List<Future<Class<?>>> futures = new ArrayList<>();
            for (int i = 0; i < requests; i++) futures.add(executor.submit(call));
            start.countDown();

            List<Class<?>> results = new ArrayList<>();
            for (Future<Class<?>> future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertThat(results).filteredOn(WithdrawalService.WrongPasswordException.class::equals).hasSize(5);
            assertThat(results).filteredOn(LoginRateLimitException.class::equals).hasSize(3);
            assertThat(failures(userId)).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }
}
