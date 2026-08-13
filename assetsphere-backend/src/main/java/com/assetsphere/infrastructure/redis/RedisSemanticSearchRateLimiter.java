package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.common.exception.RateLimitExceededException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticSearchRateLimiter;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

@Component @RequiredArgsConstructor
class RedisSemanticSearchRateLimiter implements SemanticSearchRateLimiter {
    private static final DefaultRedisScript<List> SCRIPT = new DefaultRedisScript<>("""
        local count=redis.call('INCR', KEYS[1]); if count==1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
        if count>tonumber(ARGV[2]) then return {0, math.max(1, math.ceil(redis.call('PTTL', KEYS[1])/1000))} end; return {1,0}
        """, List.class);
    private final StringRedisTemplate redis; private final SemanticIndexProperties properties;
    public void check(UUID workspaceId, UUID userId) { try { var r=redis.execute(SCRIPT,List.of("assetsphere:rate-limit:semantic-search:"+workspaceId+":"+userId),Long.toString(properties.getSemanticSearchRateLimit().getWindow().toMillis()),Integer.toString(properties.getSemanticSearchRateLimit().getPermits())); if(r==null) throw new ServiceUnavailableException("Semantic search rate limiting is unavailable",null); if(((Number)r.getFirst()).longValue()==0) throw new RateLimitExceededException(((Number)r.get(1)).longValue()); } catch(RateLimitExceededException|ServiceUnavailableException e){throw e;} catch(DataAccessException e){throw new ServiceUnavailableException("Semantic search rate limiting is unavailable",e);} }
}
