package com.petroad.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

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

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=rate-limiter-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=rate_limit_test", "spring.flyway.default-schema=rate_limit_test",
        "spring.jpa.properties.hibernate.default_schema=rate_limit_test",
        // RateLimiter는 SQL을 직접 실행하므로 DB 연결의 스키마도 맞춘다.
        "spring.datasource.hikari.schema=rate_limit_test"
})
class RateLimiterTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }

    @Autowired RateLimiter limiter;
    @Autowired TransactionTemplate transaction;
    @Autowired JdbcTemplate jdbc;
    @MockBean Clock clock;

    private static final Duration WINDOW = Duration.ofMinutes(15);
    private final Instant now = Instant.parse("2026-11-26T00:00:00Z");

    @BeforeEach void time() { at(now); }

    private void at(Instant instant) { when(clock.instant()).thenReturn(instant); }

    /** 상한 안이면 기록을 남기고 true, 넘으면 false. */
    private boolean attempt(String bucket, String key) {
        try {
            transaction.executeWithoutResult(status -> {
                limiter.check(bucket, key, 5, WINDOW);
                limiter.hit(bucket, key);
            });
            return true;
        } catch (LoginRateLimitException e) {
            return false;
        }
    }

    private int count(String key) {
        return jdbc.queryForObject("SELECT count(*) FROM rate_limit_hits WHERE key = ?", Integer.class, key);
    }

    @Test void blocksAfterMaxAndReleasesWhenTheOldestHitLeavesTheWindow() {
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) {
            at(now.plusSeconds(i));
            assertThat(attempt("test", key)).isTrue();
        }

        at(now.plusSeconds(60));
        assertThatThrownBy(() -> transaction.executeWithoutResult(status -> limiter.check("test", key, 5, WINDOW)))
                .isInstanceOfSatisfying(LoginRateLimitException.class,
                        e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(WINDOW.toSeconds() - 60));

        at(now.plus(WINDOW));
        assertThat(attempt("test", key)).isTrue();
    }

    @Test void countsEachBucketAndKeySeparately() {
        String key = UUID.randomUUID().toString();
        for (int i = 0; i < 5; i++) assertThat(attempt("one", key)).isTrue();

        assertThat(attempt("one", key)).isFalse();
        assertThat(attempt("two", key)).isTrue();
        assertThat(attempt("one", UUID.randomUUID().toString())).isTrue();
    }

    @Test void requiresTheCallersTransaction() {
        assertThatThrownBy(() -> limiter.check("test", "key", 5, WINDOW))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThatThrownBy(() -> limiter.hit("test", "key"))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test void parallelRequestsCannotExceedTheLimit() throws Exception {
        String key = UUID.randomUUID().toString();
        int requests = 8; // 기본 DB 연결 수(10)보다 적게 둔다.
        ExecutorService executor = Executors.newFixedThreadPool(requests);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Boolean> call = () -> {
                start.await();
                try {
                    transaction.executeWithoutResult(status -> {
                        limiter.check("parallel", key, 5, WINDOW);
                        pause(); // 잠금이 없으면 다른 요청이 이 틈에 통과한다.
                        limiter.hit("parallel", key);
                    });
                    return true;
                } catch (LoginRateLimitException e) {
                    return false;
                }
            };
            List<Future<Boolean>> results = new ArrayList<>();
            for (int i = 0; i < requests; i++) results.add(executor.submit(call));
            start.countDown();

            int allowed = 0;
            for (Future<Boolean> result : results) if (result.get(10, TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(5);
            assertThat(count(key)).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test void deletesOnlyHitsOlderThanOneDay() {
        String oldKey = UUID.randomUUID().toString();
        String recentKey = UUID.randomUUID().toString();
        at(now.minus(Duration.ofDays(1)).minusSeconds(1));
        assertThat(attempt("cleanup", oldKey)).isTrue();
        at(now.minus(Duration.ofHours(23)));
        assertThat(attempt("cleanup", recentKey)).isTrue();

        at(now);
        limiter.deleteExpired();

        assertThat(count(oldKey)).isZero();
        assertThat(count(recentKey)).isEqualTo(1);
    }

    private static void pause() {
        try { Thread.sleep(100); }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
