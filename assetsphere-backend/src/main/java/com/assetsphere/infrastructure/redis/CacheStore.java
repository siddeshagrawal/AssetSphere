package com.assetsphere.infrastructure.redis;

import java.time.Duration;
import java.util.Optional;

public interface CacheStore {
    <T> Optional<T> get(String key, Class<T> type);

    void put(String key, Object value, Duration ttl);

    void evict(String key);
}
