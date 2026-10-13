package com.example.exam.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Keeps the id (jti) of logged-out access tokens in Redis until the token would
 * have expired on its own. That is the smallest amount of state needed to make
 * "logout" meaningful for an otherwise stateless JWT.
 *
 * <p>Fail-open by design: when Redis is unreachable the token is accepted and
 * simply expires after {@code app.jwt.access-token-minutes}. A cache outage must
 * not log every user out (and must not break authentication entirely).
 */
@Service
public class TokenRevocationService {
    private static final Logger log = LoggerFactory.getLogger(TokenRevocationService.class);
    private static final String KEY_PREFIX = "online-exam:auth:revoked:";

    private final StringRedisTemplate redis;

    public TokenRevocationService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** Marks {@code tokenId} as revoked; the key disappears when the token expires. */
    public void revoke(String tokenId, long ttlSeconds) {
        if (tokenId == null || tokenId.isBlank() || ttlSeconds <= 0) return;
        try {
            redis.opsForValue().set(KEY_PREFIX + tokenId, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception ex) {
            log.warn("Failed to record token revocation for jti={}; the token stays valid until it expires: {}",
                    tokenId, ex.toString());
        }
    }

    public boolean isRevoked(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) return false;
        try {
            return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + tokenId));
        } catch (Exception ex) {
            log.warn("Failed to check token revocation for jti={}; accepting the token: {}", tokenId, ex.toString());
            return false;
        }
    }
}
