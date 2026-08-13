package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexingLock;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisSemanticIndexingLock implements SemanticIndexingLock {
    private static final String PREFIX = "assetsphere:lock:asset-semantic-index";
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final SemanticIndexProperties properties;
    @Override public LockHandle tryAcquire(UUID assetVersionId) {
        String key = PREFIX + ':' + assetVersionId;
        String token = UUID.randomUUID().toString();
        try {
            boolean acquired = Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key, token, properties.getProcessingLockLease()));
            return new Handle(redis, key, token, acquired);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ServiceUnavailableException("Semantic indexing lock is unavailable", exception);
        }
    }
    private record Handle(StringRedisTemplate redis, String key, String token, boolean acquired) implements LockHandle {
        @Override public void close() { if (acquired) redis.execute(RELEASE, List.of(key), token); }
    }
}
