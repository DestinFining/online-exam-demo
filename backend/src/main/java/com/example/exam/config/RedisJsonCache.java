package com.example.exam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Small, best-effort JSON cache backed by Redis.
 * Database reads remain the source of truth; Redis failures never fail a business request.
 */
@Component
public class RedisJsonCache {
    private static final Logger log = LoggerFactory.getLogger(RedisJsonCache.class);
    private static final String KEY_PREFIX = "online-exam:cache:";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final Object transactionResourceKey = new Object();

    public RedisJsonCache(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /**
     * Returns -1 when Redis is unavailable, allowing callers to bypass cache entirely.
     * Missing version keys start at version zero.
     */
    public long version(String namespace) {
        try {
            String value = redis.opsForValue().get(versionKey(namespace));
            return value == null ? 0L : Long.parseLong(value);
        } catch (Exception ex) {
            log.debug("Redis cache unavailable while reading version for {}: {}", namespace, ex.toString());
            return -1L;
        }
    }

    public <T> Optional<T> get(String key, Class<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception ex) {
            evictBrokenEntry(key);
            log.debug("Redis cache read failed for {}: {}", key, ex.toString());
            return Optional.empty();
        }
    }

    public <T> Optional<T> get(String key, TypeReference<T> type) {
        try {
            String json = redis.opsForValue().get(key);
            if (json == null || json.isBlank()) return Optional.empty();
            return Optional.ofNullable(objectMapper.readValue(json, type));
        } catch (Exception ex) {
            evictBrokenEntry(key);
            log.debug("Redis cache read failed for {}: {}", key, ex.toString());
            return Optional.empty();
        }
    }

    public void put(String key, Object value, Duration ttl) {
        try {
            String json = objectMapper.writeValueAsString(value);
            redis.opsForValue().set(key, json, ttl);
        } catch (Exception ex) {
            log.debug("Redis cache write skipped for {}: {}", key, ex.toString());
        }
    }

    /**
     * Invalidates a namespace only after a surrounding DB transaction commits.
     * Several updates inside one transaction are coalesced into a single version bump.
     */
    @SuppressWarnings("unchecked")
    public void invalidateAfterCommit(String namespace) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            bumpVersion(namespace);
            return;
        }

        Set<String> pending = (Set<String>) TransactionSynchronizationManager.getResource(transactionResourceKey);
        if (pending == null) {
            pending = new LinkedHashSet<>();
            TransactionSynchronizationManager.bindResource(transactionResourceKey, pending);
            Set<String> namespaces = pending;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    namespaces.forEach(RedisJsonCache.this::bumpVersion);
                }

                @Override
                public void afterCompletion(int status) {
                    if (TransactionSynchronizationManager.hasResource(transactionResourceKey)) {
                        TransactionSynchronizationManager.unbindResource(transactionResourceKey);
                    }
                }
            });
        }
        pending.add(namespace);
    }

    private void bumpVersion(String namespace) {
        try {
            redis.opsForValue().increment(versionKey(namespace));
        } catch (Exception ex) {
            // A short cache TTL bounds staleness if Redis is temporarily unavailable.
            log.debug("Redis cache invalidation deferred for {}: {}", namespace, ex.toString());
        }
    }

    private String versionKey(String namespace) {
        return KEY_PREFIX + "version:" + namespace;
    }

    private void evictBrokenEntry(String key) {
        try {
            redis.delete(key);
        } catch (Exception ignored) {
            // Best-effort cleanup only.
        }
    }
}
