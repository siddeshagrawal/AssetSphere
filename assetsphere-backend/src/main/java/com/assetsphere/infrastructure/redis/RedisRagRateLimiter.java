package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.common.exception.RateLimitExceededException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.intelligence.api.RagRateLimiter;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class RedisRagRateLimiter implements RagRateLimiter {

    private static final String KEY_PREFIX = "assetsphere:rate-limit:rag";
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
            local count=redis.call('INCR', KEYS[1]); if count==1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            if count>tonumber(ARGV[2]) then return {0, math.max(1, math.ceil(redis.call('PTTL', KEYS[1])/1000))} end; return {1,0}
            """, List.class);

    private final StringRedisTemplate redis;
    private final SemanticIndexProperties properties;

    @Override
    public void check(UUID workspaceId, UUID userId) {
        try {
            var limits = properties.getRagRateLimit();
            List<?> result = redis.execute(SCRIPT,
                    List.of(KEY_PREFIX + ":" + workspaceId + ":" + userId),
                    Long.toString(limits.getWindow().toMillis()), Integer.toString(limits.getPermits()));
            if (result == null) {
                throw new ServiceUnavailableException("RAG rate limiting is unavailable", null);
            }
            if (((Number) result.getFirst()).longValue() == 0) {
                throw new RateLimitExceededException(((Number) result.get(1)).longValue());
            }
        } catch (RateLimitExceededException | ServiceUnavailableException exception) {
            throw exception;
        } catch (DataAccessException exception) {
            throw new ServiceUnavailableException("RAG rate limiting is unavailable", exception);
        }
    }
}
