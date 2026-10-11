package com.petroad.backend.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayDeque;
import java.util.Deque;

@Component
public class LoginThrottle {
    private final LoginThrottleProperties properties;
    private final Clock clock;
    private final Map<String, Account> accounts = new HashMap<>();
    private final Map<String, IpWindow> ips = new HashMap<>();
    private Instant nextCleanup = Instant.MIN;

    @Autowired
    public LoginThrottle(LoginThrottleProperties properties) { this(properties, Clock.systemUTC()); }

    LoginThrottle(LoginThrottleProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public synchronized Attempt begin(String email, String ip) {
        Instant now = clock.instant();
        cleanup(now);
        IpWindow window = ips.get(ip);
        if (window == null || !now.isBefore(window.expires)) {
            if (window == null && ips.size() >= properties.maxTrackedEntries()) throw new LoginRateLimitException(60);
            window = new IpWindow(now.plus(properties.ipWindow()));
            ips.put(ip, window);
        }
        if (window.attempts >= properties.ipMaxAttempts()) throw limited(now, window.expires);
        window.attempts++;

        // Keep account keys identical to the repository lookup; do not lowercase only the limiter key.
        Account account = accounts.get(email);
        if (account == null) {
            if (accounts.size() >= properties.maxTrackedEntries()) throw new LoginRateLimitException(60);
            account = new Account(now.plus(properties.failureWindow()));
            accounts.put(email, account);
        }
        if (account.lockedUntil != null && now.isBefore(account.lockedUntil)) throw limited(now, account.lockedUntil);
        if (account.lockedUntil != null) {
            account.failures.clear();
            account.lockedUntil = null;
        }
        pruneFailures(account, now);
        // Reserve a slot before BCrypt/DB work so parallel requests cannot bypass the threshold.
        if (account.failures.size() + account.pending >= properties.maxFailures()) throw new LoginRateLimitException(1);
        account.expires = now.plus(properties.failureWindow());
        account.pending++;
        return new Attempt(account);
    }

    private synchronized void finish(Attempt attempt, boolean failed) {
        if (attempt.finished) return;
        attempt.finished = true;
        Account account = attempt.account;
        account.pending--;
        if (failed) {
            Instant now = clock.instant();
            pruneFailures(account, now);
            account.failures.addLast(now);
            account.expires = now.plus(properties.failureWindow());
            if (account.failures.size() >= properties.maxFailures() && account.lockedUntil == null) {
                account.lockedUntil = now.plus(properties.lockDuration());
            }
        } else if (account.lockedUntil == null) {
            account.failures.clear();
        }
    }

    private void pruneFailures(Account account, Instant now) {
        Instant cutoff = now.minus(properties.failureWindow());
        while (!account.failures.isEmpty() && !account.failures.peekFirst().isAfter(cutoff)) account.failures.removeFirst();
    }

    private void cleanup(Instant now) {
        if (now.isBefore(nextCleanup)) return;
        ips.values().removeIf(window -> !now.isBefore(window.expires));
        accounts.values().removeIf(account -> account.pending == 0
                && !now.isBefore(account.expires)
                && (account.lockedUntil == null || !now.isBefore(account.lockedUntil)));
        nextCleanup = now.plusSeconds(1);
    }

    private static LoginRateLimitException limited(Instant now, Instant until) {
        return new LoginRateLimitException((Duration.between(now, until).toMillis() + 999) / 1000);
    }

    public final class Attempt implements AutoCloseable {
        private final Account account;
        private boolean finished;
        private Attempt(Account account) { this.account = account; }
        public void failed() { finish(this, true); }
        public void succeeded() { finish(this, false); }
        // Infrastructure errors release the reservation without recording a bad password or clearing failures.
        @Override public void close() {
            synchronized (LoginThrottle.this) {
                if (!finished) { finished = true; account.pending--; }
            }
        }
    }

    private static final class Account {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private int pending;
        private Instant expires;
        private Instant lockedUntil;
        private Account(Instant expires) { this.expires = expires; }
    }

    private static final class IpWindow {
        private int attempts;
        private final Instant expires;
        private IpWindow(Instant expires) { this.expires = expires; }
    }
}
