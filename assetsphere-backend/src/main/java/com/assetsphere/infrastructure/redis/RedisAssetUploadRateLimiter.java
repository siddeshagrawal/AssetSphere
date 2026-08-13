package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.asset.api.AssetUploadProperties;
import com.assetsphere.modules.asset.api.AssetUploadRateLimiter;
import com.assetsphere.modules.common.exception.RateLimitExceededException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisAssetUploadRateLimiter implements AssetUploadRateLimiter {

    private static final String KEY_PREFIX = "assetsphere:rate-limit:asset-upload";

    private static final DefaultRedisScript<List> SLIDING_WINDOW_SCRIPT = new DefaultRedisScript<>("""
            local now = tonumber(ARGV[1])
            local window = tonumber(ARGV[2])
            local limit = tonumber(ARGV[3])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, now - window)
            local count = redis.call('ZCARD', KEYS[1])
            if count >= limit then
                local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')
                local retry = math.max(1, math.ceil((tonumber(oldest[2]) + window - now) / 1000))
                return {0, retry}
            end
            redis.call('ZADD', KEYS[1], now, ARGV[4])
            redis.call('PEXPIRE', KEYS[1], window)
            return {1, 0}
            """, List.class);

    private final StringRedisTemplate redisTemplate;
    private final AssetUploadProperties properties;

    @Override
    public void check(UUID workspaceId, UUID userId) {
        long now = System.currentTimeMillis();
        long windowMillis = properties.getRateLimit().getWindow().toMillis();
        try {
            List<?> result = redisTemplate.execute(
                    SLIDING_WINDOW_SCRIPT,
                    List.of(key(workspaceId, userId)),
                    Long.toString(now),
                    Long.toString(windowMillis),
                    Integer.toString(properties.getRateLimit().getPermits()),
                    now + ":" + UUID.randomUUID()
            );
            if (result == null || result.size() != 2) {
                throw new ServiceUnavailableException("Upload rate limiting is unavailable", null);
            }
            if (((Number) result.getFirst()).longValue() == 0L) {
                throw new RateLimitExceededException(((Number) result.get(1)).longValue());
            }
        } catch (RateLimitExceededException | ServiceUnavailableException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("Upload rate limiting is unavailable", exception);
        }
    }

    private String key(UUID workspaceId, UUID userId) {
        return "%s:%s:%s".formatted(KEY_PREFIX, workspaceId, userId);
    }
}
