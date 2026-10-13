package com.example.exam.config;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisJsonCacheTest {

    @Test
    void missingVersionStartsAtZero() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("online-exam:cache:version:questions")).thenReturn(null);

        RedisJsonCache cache = new RedisJsonCache(redis, new ObjectMapper());

        assertEquals(0L, cache.version("questions"));
    }

    @Test
    void readsJsonCollectionsFromRedis() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("online-exam:catalog:subjects:v0:enabled:true"))
                .thenReturn("[\"Java程序设计\",\"数据库原理\"]");

        RedisJsonCache cache = new RedisJsonCache(redis, new ObjectMapper());
        var result = cache.get("online-exam:catalog:subjects:v0:enabled:true", new TypeReference<List<String>>() {});

        assertTrue(result.isPresent());
        assertEquals(List.of("Java程序设计", "数据库原理"), result.orElseThrow());
    }

    @Test
    void redisReadFailureDisablesCachingInsteadOfFailingTheRequest() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new IllegalStateException("Redis is unavailable"));

        RedisJsonCache cache = new RedisJsonCache(redis, new ObjectMapper());

        assertEquals(-1L, cache.version("questions"));
        assertTrue(cache.get("online-exam:catalog:test", String.class).isEmpty());
    }
}
