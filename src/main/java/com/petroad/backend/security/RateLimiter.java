package com.petroad.backend.security;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * DB에 기록을 남기는 공용 횟수 제한기.
 * check와 hit은 같은 트랜잭션에서 호출해야 한다. check가 잡은 키별 잠금은 그 트랜잭션이 끝날 때 풀린다.
 * 요청이 실패해도 기록을 남겨야 하면, 호출하는 쪽에서 hit 이후의 예외에도 트랜잭션이 커밋되게 해야 한다.
 */
@Component
public class RateLimiter {
    private static final Duration RETENTION = Duration.ofDays(1);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public RateLimiter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record Usage(long count, OffsetDateTime oldest) {}

    /**
     * 확인과 기록을 한 번에 하고 기록의 ID를 돌려준다. 트랜잭션 밖에서 호출하면 바로 저장되고 잠금도 풀리며,
     * 이후 처리가 실패해도 기록이 남는다. 성공한 요청만 세려면 실패했을 때 돌려받은 ID로 cancel을 호출한다.
     */
    @Transactional
    public long consume(String bucket, String key, int max, Duration window) {
        check(bucket, key, max, window);
        return hit(bucket, key);
    }

    /** consume으로 먼저 잡아 둔 기록 하나를 취소한다. */
    @Transactional
    public void cancel(long hitId) {
        jdbc.update("DELETE FROM rate_limit_hits WHERE id = ?", hitId);
    }

    /** 최근 window 동안의 기록이 max개 이상이면 LoginRateLimitException을 던진다. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void check(String bucket, String key, int max, Duration window) {
        // 같은 키의 요청은 앞선 트랜잭션이 끝날 때까지 여기서 기다린다.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtext(?))",
                (ResultSetExtractor<Void>) rs -> null, bucket + key);

        Instant now = clock.instant();
        Usage usage = jdbc.queryForObject(
                "SELECT count(*), min(hit_at) FROM rate_limit_hits WHERE bucket = ? AND key = ? AND hit_at > ?",
                (rs, row) -> new Usage(rs.getLong(1), rs.getObject(2, OffsetDateTime.class)),
                bucket, key, utc(now.minus(window)));

        if (usage.count() >= max) {
            Instant availableAt = usage.oldest().toInstant().plus(window);
            long retryAfterSeconds = (Duration.between(now, availableAt).toMillis() + 999) / 1000;
            throw new LoginRateLimitException(retryAfterSeconds);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long hit(String bucket, String key) {
        return jdbc.queryForObject("INSERT INTO rate_limit_hits (bucket, key, hit_at) VALUES (?, ?, ?) RETURNING id",
                Long.class, bucket, key, utc(clock.instant()));
    }

    /** 매시 정각에 하루 넘은 기록을 지운다. */
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void deleteExpired() {
        jdbc.update("DELETE FROM rate_limit_hits WHERE hit_at < ?", utc(clock.instant().minus(RETENTION)));
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
