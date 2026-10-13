package com.example.exam.security;

import com.example.exam.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Counts consecutive failed logins per username and temporarily blocks further
 * attempts, turning password guessing from "unlimited requests" into
 * {@value #MAX_FAILURES} attempts per {@value #WINDOW_MINUTES} minutes.
 *
 * <p>The counter lives in Redis behind a sliding window: every failure refreshes
 * the TTL. Redis failures are fail-open — locking every account out because the
 * cache is down would be worse than accepting a few unthrottled attempts.
 *
 * <p>Trade-off: keying on the username lets an attacker who knows an account name
 * keep that account locked. {@link #MAX_FAILURES} and {@link #WINDOW} are the
 * knobs that trade that denial-of-service risk against brute-force resistance.
 */
@Service
public class LoginAttemptService {
    private static final Logger log = LoggerFactory.getLogger(LoginAttemptService.class);

    static final int MAX_FAILURES = 5;
    static final long WINDOW_MINUTES = 15;
    static final Duration WINDOW = Duration.ofMinutes(WINDOW_MINUTES);
    private static final String KEY_PREFIX = "online-exam:auth:failed-login:";

    private final StringRedisTemplate redis;

    public LoginAttemptService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Throws 429 once the username has accumulated {@link #MAX_FAILURES} recent failures. */
    public void assertNotLocked(String username) {
        long failures;
        try {
            String value = redis.opsForValue().get(key(username));
            failures = value == null ? 0L : Long.parseLong(value);
        } catch (Exception ex) {
            log.warn("Failed to read the login attempt counter for {}; allowing this attempt: {}",
                    username, ex.toString());
            return;
        }
        if (failures >= MAX_FAILURES) {
            throw new BusinessException(429, "登录失败次数过多，请约 " + WINDOW.toMinutes() + " 分钟后再试");
        }
    }

    /** Records one failure and restarts the window. */
    public void recordFailure(String username) {
        String key = key(username);
        try {
            redis.opsForValue().increment(key);
            redis.expire(key, WINDOW);
        } catch (Exception ex) {
            log.warn("Failed to record a failed login for {}: {}", username, ex.toString());
        }
    }

    /** Clears the counter after a successful login or a password change. */
    public void clear(String username) {
        try {
            redis.delete(key(username));
        } catch (Exception ex) {
            log.warn("Failed to clear the login attempt counter for {}: {}", username, ex.toString());
        }
    }

    /**
     * MySQL compares usernames with a case-insensitive collation, so the counter
     * must be case-insensitive as well — otherwise "Admin" would bypass the
     * counter that "admin" accumulated.
     */
    private String key(String username) {
        return KEY_PREFIX + (username == null ? "" : username.trim().toLowerCase());
    }
}
