package com.example.exam.security;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TokenRevocationServiceTest {
    private static final String REVOKED_KEY = "online-exam:auth:revoked:jti-1";

    @Test
    void storesTheTokenIdOnlyUntilTheTokenWouldHaveExpired() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);

        new TokenRevocationService(redis).revoke("jti-1", 600);

        verify(values).set(REVOKED_KEY, "1", Duration.ofSeconds(600));
    }

    @Test
    void ignoresTokensWithoutAnIdOrWithoutRemainingLife() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        TokenRevocationService service = new TokenRevocationService(redis);

        service.revoke(null, 600);
        service.revoke("   ", 600);
        service.revoke("jti-1", 0);
        service.revoke("jti-1", -5);

        verify(redis, never()).opsForValue();
    }

    @Test
    void reportsRevokedTokensAndBlankIds() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(REVOKED_KEY)).thenReturn(true);
        when(redis.hasKey("online-exam:auth:revoked:jti-2")).thenReturn(false);
        TokenRevocationService service = new TokenRevocationService(redis);

        assertTrue(service.isRevoked("jti-1"));
        assertFalse(service.isRevoked("jti-2"));
        assertFalse(service.isRevoked(null));
        assertFalse(service.isRevoked("   "));
    }

    @Test
    void redisFailureAcceptsTheTokenInsteadOfBreakingAuthentication() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.hasKey(REVOKED_KEY)).thenThrow(new IllegalStateException("Redis is unavailable"));

        assertFalse(new TokenRevocationService(redis).isRevoked("jti-1"));
    }
}
