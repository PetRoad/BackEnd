package com.petroad.backend.security;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class LoginThrottle {
    private static final String IP = "login-ip";
    private static final String ACCOUNT = "login-account";
    private static final String ACCOUNT_IP = "login-account-ip";

    private final LoginThrottleProperties properties;
    private final RateLimiter limiter;
    private final PlatformTransactionManager transactions;
    private final TransactionTemplate ipTransactions;

    public LoginThrottle(LoginThrottleProperties properties, RateLimiter limiter,
                         PlatformTransactionManager transactions) {
        this.properties = properties;
        this.limiter = limiter;
        this.transactions = transactions;
        this.ipTransactions = new TransactionTemplate(transactions);
        this.ipTransactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** 반드시 요청 스레드의 try-with-resources 안에서 사용한다. */
    public Attempt begin(String email, String ip) {
        String ipKey = ClientIpResolver.rateLimitKey(ip);
        // 계정 잠금으로 거절된 요청과 성공한 로그인도 IP 요청 수에 포함한다.
        // 인증 트랜잭션이 롤백돼도 이 기록은 남는다.
        ipTransactions.executeWithoutResult(status ->
                limiter.consume(IP, ipKey, properties.ipMaxAttempts(), properties.ipWindow()));

        // 조회와 같은 대소문자를 유지하고, 이메일 원문 대신 고정 길이 해시를 저장한다.
        String accountKey = accountKey(email);
        String pairKey = accountKey + "|" + ipKey;
        var definition = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        TransactionStatus transaction = transactions.getTransaction(definition);
        try {
            // 항상 계정 -> 계정+IP 순으로 잠근다. 비밀번호 판단이 끝날 때까지 잠금을
            // 유지하므로 여러 서버/동시 요청도 아직 확정되지 않은 실패를 추월하지 못한다.
            limiter.check(ACCOUNT, accountKey, properties.accountMaxFailures(), properties.accountFailureWindow());
            limiter.check(ACCOUNT_IP, pairKey, properties.maxFailures(), properties.failureWindow());
            return new Attempt(transaction, accountKey, pairKey);
        } catch (RuntimeException | Error e) {
            if (!transaction.isCompleted()) transactions.rollback(transaction);
            throw e;
        }
    }

    private static String accountKey(String email) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(email.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", e);
        }
    }

    public final class Attempt implements AutoCloseable {
        private final TransactionStatus transaction;
        private final String accountKey;
        private final String pairKey;
        private boolean finished;

        private Attempt(TransactionStatus transaction, String accountKey, String pairKey) {
            this.transaction = transaction;
            this.accountKey = accountKey;
            this.pairKey = pairKey;
        }

        public void failed() {
            finish(() -> {
                limiter.hit(ACCOUNT_IP, pairKey);
                limiter.hit(ACCOUNT, accountKey);
            });
        }

        public void succeeded() {
            // 다른 IP의 실패와 계정 전체 실패 이력은 성공 한 번으로 지우지 않는다.
            finish(() -> limiter.reset(ACCOUNT_IP, pairKey));
        }

        private void finish(Runnable update) {
            if (finished) return;
            try {
                update.run();
                // failed는 컨트롤러의 401 예외보다 먼저 커밋해 실패 이력이 롤백되지 않게 한다.
                transactions.commit(transaction);
            } catch (RuntimeException | Error e) {
                if (!transaction.isCompleted()) transactions.rollback(transaction);
                throw e;
            } finally {
                finished = true;
            }
        }

        /** DB/JWT 등 내부 오류는 비밀번호 실패로 세지 않고, 기존 실패도 지우지 않는다. */
        @Override public void close() {
            if (!finished) {
                finished = true;
                if (!transaction.isCompleted()) transactions.rollback(transaction);
            }
        }
    }
}
