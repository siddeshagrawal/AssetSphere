package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.intelligence.api.IntelligenceProcessingLock;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisIntelligenceProcessingLock implements IntelligenceProcessingLock {

    private static final String KEY_PREFIX = "assetsphere:lock:asset-intelligence";
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final IntelligenceProperties properties;

    @Override
    public LockHandle tryAcquire(UUID assetVersionId) {
        String key = KEY_PREFIX + ':' + assetVersionId;
        String token = UUID.randomUUID().toString();
        try {
            boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key, token, properties.getProcessingLockLease()
            ));
            return new RedisLockHandle(redisTemplate, key, token, acquired);
        } catch (org.springframework.dao.DataAccessException exception) {
            throw new ServiceUnavailableException("Asset intelligence lock is unavailable", exception);
        }
    }

    private record RedisLockHandle(StringRedisTemplate redisTemplate, String key, String token,
                                   boolean acquired) implements LockHandle {
        @Override
        public void close() {
            if (acquired) {
                redisTemplate.execute(RELEASE_SCRIPT, List.of(key), token);
            }
        }
    }
}
