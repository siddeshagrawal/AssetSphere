package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.processing.api.AssetProcessingLock;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisAssetProcessingLock implements AssetProcessingLock {

    private static final String KEY_PREFIX = "assetsphere:lock:asset-processing";

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final ProcessingProperties properties;

    @Override
    public LockHandle tryAcquire(UUID assetVersionId) {
        String key = KEY_PREFIX + ':' + assetVersionId;
        String token = UUID.randomUUID().toString();
        try {
            boolean acquired = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(
                    key, token, properties.getConsumer().getProcessingLockLease()
            ));
            return new RedisLockHandle(redisTemplate, key, token, acquired);
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("Asset processing lock is unavailable", exception);
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
