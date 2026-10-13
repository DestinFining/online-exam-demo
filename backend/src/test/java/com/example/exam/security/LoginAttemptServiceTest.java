package com.example.exam.security;

import com.example.exam.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginAttemptServiceTest {
    private static final String KEY = "online-exam:auth:failed-login:admin";

    @SuppressWarnings("unchecked")
    private static ValueOperations<String, String> valueOperations(StringRedisTemplate redis) {
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        return values;
    }

    @Test
    void rejectsOnceTheFailureLimitIsReached() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(valueOperations(redis).get(KEY)).thenReturn("5");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> new LoginAttemptService(redis).assertNotLocked("admin"));

        assertEquals(429, ex.getCode());
    }

    @Test
    void allowsAttemptsBelowTheLimit() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(valueOperations(redis).get(KEY)).thenReturn("4");

        assertDoesNotThrow(() -> new LoginAttemptService(redis).assertNotLocked("admin"));
    }

    @Test
    void countsFailuresCaseInsensitivelyAndRefreshesTheWindow() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = valueOperations(redis);

        new LoginAttemptService(redis).recordFailure(" Admin ");

        verify(values).increment(KEY);
        verify(redis).expire(KEY, Duration.ofMinutes(15));
    }

    @Test
    void redisFailureFailsOpenInsteadOfLockingEveryAccountOut() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis is unavailable"));
        LoginAttemptService service = new LoginAttemptService(redis);

        assertDoesNotThrow(() -> service.assertNotLocked("admin"));
        assertDoesNotThrow(() -> service.recordFailure("admin"));
        assertDoesNotThrow(() -> service.clear("admin"));
    }
}
