package com.petroad.backend.security;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class LoginThrottleTest {
    private final MutableClock clock = new MutableClock();
    static LoginThrottleProperties settings(int failures, int ipAttempts, int capacity) {
        return new LoginThrottleProperties(failures, Duration.ofMinutes(15), Duration.ofMinutes(15),
                ipAttempts, Duration.ofMinutes(1), capacity, List.of());
    }

    @Test void accountFailuresApplyAcrossIpsAndExpireWithoutPermanentLock() {
        LoginThrottle throttle = new LoginThrottle(settings(5, 30, 100), clock);
        for (int n = 0; n < 5; n++) {
            try (var attempt = throttle.begin("user@example.com", "192.0.2." + n)) { attempt.failed(); }
        }
        assertThatThrownBy(() -> throttle.begin("user@example.com", "192.0.2.99"))
                .isInstanceOfSatisfying(LoginRateLimitException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(900));
        clock.advance(Duration.ofMinutes(1));
        assertThatThrownBy(() -> throttle.begin("user@example.com", "192.0.2.99"))
                .isInstanceOfSatisfying(LoginRateLimitException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(840));
        clock.advance(Duration.ofMinutes(14));
        try (var attempt = throttle.begin("user@example.com", "192.0.2.99")) { attempt.succeeded(); }
    }

    @Test void ipAttemptsApplyAcrossAccountsIncludingSuccessfulLogins() {
        LoginThrottle throttle = new LoginThrottle(settings(5, 2, 100), clock);
        for (int n = 0; n < 2; n++) {
            try (var attempt = throttle.begin("user" + n, "192.0.2.1")) { attempt.succeeded(); }
        }
        assertThatThrownBy(() -> throttle.begin("another-user", "192.0.2.1"))
                .isInstanceOfSatisfying(LoginRateLimitException.class,
                        error -> assertThat(error.getRetryAfterSeconds()).isEqualTo(60));
        clock.advance(Duration.ofMinutes(1));
        try (var attempt = throttle.begin("another-user", "192.0.2.1")) { attempt.succeeded(); }
    }

    @Test void successfulLoginResetsFailuresButAnInfrastructureErrorDoesNot() {
        LoginThrottle throttle = new LoginThrottle(settings(2, 30, 100), clock);
        try (var attempt = throttle.begin("user", "192.0.2.1")) { attempt.failed(); }
        try (var attempt = throttle.begin("user", "192.0.2.1")) { attempt.succeeded(); }
        try (var attempt = throttle.begin("user", "192.0.2.1")) { attempt.failed(); }
        // Simulate a DB/JWT error: release only the in-flight reservation.
        try (var ignored = throttle.begin("user", "192.0.2.1")) {}
        try (var attempt = throttle.begin("user", "192.0.2.1")) { attempt.failed(); }
        assertThatThrownBy(() -> throttle.begin("user", "192.0.2.2"))
                .isInstanceOf(LoginRateLimitException.class);
    }

    @Test void failuresUseASlidingObservationWindow() {
        LoginThrottle throttle = new LoginThrottle(settings(2, 30, 100), clock);
        try (var ignored = throttle.begin("user", "ip")) {}
        clock.advance(Duration.ofMinutes(14));
        try (var attempt = throttle.begin("user", "ip")) { attempt.failed(); }
        clock.advance(Duration.ofMinutes(2));
        try (var attempt = throttle.begin("user", "ip")) { attempt.failed(); }
        assertThatThrownBy(() -> throttle.begin("user", "other-ip")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test void oldFailuresExpireBeforeThreshold() {
        LoginThrottle throttle = new LoginThrottle(settings(2, 30, 100), clock);
        try (var attempt = throttle.begin("user", "ip")) { attempt.failed(); }
        clock.advance(Duration.ofMinutes(15));
        try (var attempt = throttle.begin("user", "ip")) { attempt.failed(); }
        try (var attempt = throttle.begin("user", "ip")) { attempt.succeeded(); }
    }

    @Test void concurrentRequestsCannotExceedAccountReservations() throws Exception {
        LoginThrottle throttle = new LoginThrottle(settings(5, 100, 100), clock);
        CountDownLatch ready = new CountDownLatch(20), start = new CountDownLatch(1), release = new CountDownLatch(1);
        AtomicInteger allowed = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(20);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int n = 0; n < 20; n++) {
                String ip = "ip" + n;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    try {
                        start.await();
                        try (var attempt = throttle.begin("user", ip)) {
                            allowed.incrementAndGet();
                            release.await();
                            attempt.failed();
                        }
                    } catch (LoginRateLimitException expected) { }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException(e); }
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            // A barrier task would deadlock the occupied pool; wait until every begin has resolved instead.
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (allowed.get() < 5 && System.nanoTime() < deadline) Thread.onSpinWait();
            release.countDown();
            for (Future<?> future : futures) future.get(5, TimeUnit.SECONDS);
            assertThat(allowed.get()).isEqualTo(5);
        } finally { start.countDown(); release.countDown(); executor.shutdownNow(); }
        assertThatThrownBy(() -> throttle.begin("user", "new-ip")).isInstanceOf(LoginRateLimitException.class);
    }

    @Test void fullTrackingCapacityDoesNotEvictLockedAccountsAndRecoversAfterExpiry() {
        LoginThrottle throttle = new LoginThrottle(settings(1, 30, 1), clock);
        try (var attempt = throttle.begin("locked-user", "ip")) { attempt.failed(); }
        assertThatThrownBy(() -> throttle.begin("another-user", "ip")).isInstanceOf(LoginRateLimitException.class);
        assertThatThrownBy(() -> throttle.begin("locked-user", "ip")).isInstanceOf(LoginRateLimitException.class);
        clock.advance(Duration.ofMinutes(15));
        try (var attempt = throttle.begin("another-user", "ip")) { attempt.succeeded(); }
    }
}
