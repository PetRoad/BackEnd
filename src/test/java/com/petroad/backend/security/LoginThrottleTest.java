package com.petroad.backend.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import com.petroad.backend.domain.User;
import com.petroad.backend.repository.UserRepository;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.anyString;

@EnabledIfEnvironmentVariable(named = "PETROAD_DB_TEST_URL", matches = ".+")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(properties = {
        "jwt.secret=login-throttle-test-only-key-do-not-deploy-0123456789",
        "spring.datasource.username=petroad_test", "spring.datasource.password=test-only-password",
        "spring.flyway.schemas=login_throttle_test", "spring.flyway.default-schema=login_throttle_test",
        "spring.jpa.properties.hibernate.default_schema=login_throttle_test",
        "spring.datasource.hikari.schema=login_throttle_test"
})
class LoginThrottleTest {
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> System.getenv("PETROAD_DB_TEST_URL"));
    }
    @Autowired RateLimiter limiter;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RefreshTokenService refreshTokens;
    @MockBean Clock clock;
    private final Instant now = Instant.parse("2026-11-27T00:00:00Z");
    private LoginThrottle throttle;

    static LoginThrottleProperties settings(int failures, int ipAttempts, int accountFailures) {
        return new LoginThrottleProperties(failures, Duration.ofMinutes(15), accountFailures, Duration.ofHours(1),
                ipAttempts, Duration.ofMinutes(1), List.of());
    }

    @BeforeEach void setup() {
        when(clock.instant()).thenReturn(now);
        jdbc.update("DELETE FROM rate_limit_hits");
        throttle = fresh(settings(5, 30, 50));
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    private LoginThrottle fresh(LoginThrottleProperties properties) {
        return new LoginThrottle(properties, limiter, transactions);
    }

    private void fail(LoginThrottle service, String email, String ip) {
        try (var attempt = service.begin(email, ip)) { attempt.failed(); }
    }

    private void succeed(LoginThrottle service, String email, String ip) {
        try (var attempt = service.begin(email, ip)) { attempt.succeeded(); }
    }

    private void beginAndClose(LoginThrottle service, String email, String ip) {
        try (var ignored = service.begin(email, ip)) { }
    }

    private long count(String bucket) {
        return jdbc.queryForObject("SELECT count(*) FROM rate_limit_hits WHERE bucket=?", Long.class, bucket);
    }

    @Test void fiveFailuresBlockOnlyThatPairAndExpireAsTheOldestFailureLeavesTheWindow() {
        for (int n=0;n<5;n++) fail(throttle,"user@example.com","192.0.2.1");
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(900));
        succeed(throttle,"user@example.com","192.0.2.2");
        assertThat(count("login-account-ip")).isEqualTo(5);
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(840));
        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(15)));
        succeed(throttle,"user@example.com","192.0.2.1");
        assertThat(count("login-account-ip")).isZero();
        assertThat(count("login-account")).isEqualTo(5);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test void fiftyFailuresAcrossIpsBlockTheAccountForTheRemainingHour() {
        for (int n=0;n<50;n++) fail(throttle,"user@example.com","192.0.2."+(n/5+1));
        assertThat(count("login-account")).isEqualTo(50);
        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(30)));
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","198.51.100.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(1800));
        succeed(throttle,"other@example.com","198.51.100.1");
        when(clock.instant()).thenReturn(now.plus(Duration.ofHours(1)));
        succeed(throttle,"user@example.com","198.51.100.1");
    }

    @Test void successClearsOnlyItsPairAndLeavesOtherIpsAndAccountHistory() {
        for (int n=0;n<4;n++) {
            fail(throttle,"user@example.com","192.0.2.1");
            fail(throttle,"user@example.com","192.0.2.2");
        }
        succeed(throttle,"user@example.com","192.0.2.1");
        assertThat(count("login-account-ip")).isEqualTo(4);
        assertThat(count("login-account")).isEqualTo(8);
        for (int n=0;n<5;n++) fail(throttle,"user@example.com","192.0.2.1");
        fail(throttle,"user@example.com","192.0.2.2");
        for (String ip:List.of("192.0.2.1","192.0.2.2")) {
            assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com",ip)).isInstanceOf(LoginRateLimitException.class);
        }
    }

    @Test void ipRequestsIncludeSuccessesAndBlockedAccountsAndExpireAfterOneMinute() {
        for (int n=0;n<30;n++) succeed(throttle,"user"+n+"@example.com","192.0.2.1");
        assertThatThrownBy(() -> beginAndClose(throttle,"other@example.com","192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
        assertThat(count("login-ip")).isEqualTo(30);
        assertThat(count("login-account")).isZero();
        when(clock.instant()).thenReturn(now.plusSeconds(60));
        succeed(throttle,"other@example.com","192.0.2.1");
    }

    @Test void ipv6AddressesInTheSame64BitPrefixShareThePairLimit() {
        for (int n=0;n<5;n++) fail(throttle,"user@example.com","2001:db8:1:2::"+(n+1));
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","2001:db8:1:2:ffff:ffff:ffff:ffff"))
                .isInstanceOf(LoginRateLimitException.class);
        succeed(throttle,"user@example.com","2001:db8:1:3::1");
    }

    @Test void ipv6AddressesInTheSamePrefixAlsoShareTheIpRequestLimit() {
        LoginThrottle small = fresh(settings(5,2,50));
        succeed(small,"one@example.com","2001:db8:1:2::1");
        succeed(small,"two@example.com","2001:db8:1:2::2");
        assertThatThrownBy(() -> beginAndClose(small,"three@example.com","2001:db8:1:2::3"))
                .isInstanceOfSatisfying(LoginRateLimitException.class, e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
        succeed(small,"three@example.com","2001:db8:1:3::1");
    }

    @Test void infrastructureErrorReleasesTheLockWithoutRecordingOrClearingPasswordFailures() {
        for (int n=0;n<4;n++) fail(throttle,"user@example.com","192.0.2.1");
        beginAndClose(throttle,"user@example.com","192.0.2.1");
        assertThat(count("login-account-ip")).isEqualTo(4);
        assertThat(count("login-account")).isEqualTo(4);
        assertThat(count("login-ip")).isEqualTo(5);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        fail(throttle,"user@example.com","192.0.2.1");
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","192.0.2.1")).isInstanceOf(LoginRateLimitException.class);
        assertThat(count("login-ip")).isEqualTo(7);
    }

    @Test void recreatingServicesPreservesPairAccountAndIpCounters() {
        for (int n=0;n<5;n++) fail(throttle,"user@example.com","192.0.2.1");
        LoginThrottle restarted = fresh(settings(5,30,50));
        assertThatThrownBy(() -> beginAndClose(restarted,"user@example.com","192.0.2.1")).isInstanceOf(LoginRateLimitException.class);
        assertThatThrownBy(() -> beginAndClose(fresh(settings(5,30,5)),"user@example.com","192.0.2.2"))
                .isInstanceOf(LoginRateLimitException.class);
        assertThatThrownBy(() -> beginAndClose(fresh(settings(5,2,50)),"other@example.com","192.0.2.1"))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test void keysPreserveEmailCaseAndDoNotStoreTheOriginalEmail() {
        for (int n=0;n<5;n++) fail(throttle,"User@example.com","192.0.2.1");
        succeed(throttle,"user@example.com","192.0.2.1");
        assertThat(jdbc.queryForList("SELECT key FROM rate_limit_hits WHERE bucket='login-account'",String.class))
                .hasSize(5).allMatch(key -> key.matches("[0-9a-f]{64}"));
        assertThatThrownBy(() -> beginAndClose(throttle,"User@example.com","192.0.2.1")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test void parallelRequestsAcrossInstancesCannotExceedThePairLimit() throws Exception {
        int requests=8;
        ExecutorService executor=Executors.newFixedThreadPool(requests);
        CountDownLatch start=new CountDownLatch(1);
        List<Future<Boolean>> futures=new ArrayList<>();
        try {
            for (int n=0;n<requests;n++) {
                LoginThrottle instance=fresh(settings(5,100,50));
                futures.add(executor.submit(() -> {
                    start.await();
                    try { fail(instance,"user@example.com","192.0.2.1");return true; }
                    catch(LoginRateLimitException e) { return false; }
                }));
            }
            start.countDown();
            int allowed=0;
            for (Future<Boolean> future:futures) if(future.get(10,TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(5);
            assertThat(count("login-account-ip")).isEqualTo(5);
            assertThat(count("login-account")).isEqualTo(5);
            assertThat(count("login-ip")).isEqualTo(8);
        } finally {start.countDown();executor.shutdownNow();}
    }

    @Test void parallelRequestsAcrossDifferentIpsCannotExceedTheAccountLimit() throws Exception {
        ExecutorService executor=Executors.newFixedThreadPool(8);
        CountDownLatch start=new CountDownLatch(1);
        List<Future<Boolean>> futures=new ArrayList<>();
        try {
            for (int n=0;n<8;n++) {
                String ip="192.0.2."+(n+1);
                LoginThrottle instance=fresh(settings(5,100,3));
                futures.add(executor.submit(() -> {
                    start.await();
                    try {fail(instance,"user@example.com",ip);return true;}
                    catch(LoginRateLimitException e){return false;}
                }));
            }
            start.countDown();
            int allowed=0;
            for (Future<Boolean> future:futures) if(future.get(10,TimeUnit.SECONDS)) allowed++;
            assertThat(allowed).isEqualTo(3);
            assertThat(count("login-account")).isEqualTo(3);
        } finally {start.countDown();executor.shutdownNow();}
    }

    @Test void failureWriteErrorRollsBackBothCountersAndReleasesTheLock() {
        for (int n=0;n<4;n++) fail(throttle,"user@example.com","192.0.2.1");
        RateLimiter broken = mock(RateLimiter.class, org.mockito.AdditionalAnswers.delegatesTo(limiter));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("simulated DB failure"))
                .when(broken).hit(org.mockito.ArgumentMatchers.eq("login-account"),anyString());
        LoginThrottle service = new LoginThrottle(settings(5,30,50),broken,transactions);
        assertThatThrownBy(() -> fail(service,"user@example.com","192.0.2.1"))
                .isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        // 계정+IP 기록을 쓴 다음 계정 전체 기록에서 실패해도 둘 다 롤백한다.
        assertThat(count("login-account-ip")).isEqualTo(4);
        assertThat(count("login-account")).isEqualTo(4);
        assertThat(count("login-ip")).isEqualTo(5);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        fail(throttle,"user@example.com","192.0.2.1");
        assertThat(count("login-account-ip")).isEqualTo(5);
    }

    @Test void resetErrorRollsBackIssuedTokensAndRestoresThePreviousFailures() {
        User user = users.save(new User(UUID.randomUUID()+"@example.com","fixture-hash"));
        for (int n=0;n<4;n++) fail(throttle,user.getEmail(),"192.0.2.1");
        RateLimiter broken = mock(RateLimiter.class, org.mockito.AdditionalAnswers.delegatesTo(limiter));
        doAnswer(invocation -> {
            limiter.reset(invocation.getArgument(0),invocation.getArgument(1));
            throw new org.springframework.dao.DataAccessResourceFailureException("simulated failure after reset");
        }).when(broken).reset(anyString(),anyString());
        LoginThrottle service = new LoginThrottle(settings(5,30,50),broken,transactions);
        assertThatThrownBy(() -> {
            try (var attempt = service.begin(user.getEmail(),"192.0.2.1")) {
                refreshTokens.issue(user);
                attempt.succeeded();
            }
        }).isInstanceOf(org.springframework.dao.DataAccessResourceFailureException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_session WHERE user_id=?",Long.class,user.getId())).isZero();
        assertThat(count("login-account-ip")).isEqualTo(4);
        assertThat(count("login-account")).isEqualTo(4);
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
        succeed(throttle,user.getEmail(),"192.0.2.1");
        assertThat(count("login-account-ip")).isZero();
    }

    @Test void anUnfinishedAuthenticationCannotBeOvertakenByAnotherInstance() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1), secondStarted = new CountDownLatch(1);
        LoginThrottle first = fresh(settings(1,30,50)), second = fresh(settings(1,30,50));
        try {
            Future<?> holding = executor.submit(() -> {
                try (var attempt = first.begin("user@example.com","192.0.2.1")) {
                    locked.countDown();
                    if (!release.await(5,TimeUnit.SECONDS)) throw new IllegalStateException("test release timed out");
                    attempt.failed();
                } catch (InterruptedException e) { Thread.currentThread().interrupt();throw new IllegalStateException(e); }
            });
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            Future<Boolean> waiting = executor.submit(() -> {
                secondStarted.countDown();
                try { beginAndClose(second,"user@example.com","192.0.2.1");return true; }
                catch (LoginRateLimitException e) { return false; }
            });
            assertThat(secondStarted.await(5,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> waiting.get(200,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            holding.get(5,TimeUnit.SECONDS);
            assertThat(waiting.get(5,TimeUnit.SECONDS)).isFalse();
            assertThat(count("login-account-ip")).isEqualTo(1);
        } finally { release.countDown();executor.shutdownNow(); }
    }

    @Test void retryAfterUsesTheOldestFailureInsteadOfStartingAnotherFixedLock() {
        for (int n=0;n<5;n++) {
            when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(n)));
            fail(throttle,"user@example.com","192.0.2.1");
        }
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class,e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(660));
        when(clock.instant()).thenReturn(now.plus(Duration.ofMinutes(15)));
        fail(throttle,"user@example.com","192.0.2.1");
        assertThatThrownBy(() -> beginAndClose(throttle,"user@example.com","192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class,e -> assertThat(e.getRetryAfterSeconds()).isEqualTo(60));
    }
}
