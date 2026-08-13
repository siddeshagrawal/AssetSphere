package com.assetsphere.infrastructure.redis;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetCacheProperties;
import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class RedisAssetMetadataCache implements AssetMetadataCache {

    private static final String KEY_PREFIX = "assetsphere:cache:asset-metadata";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AssetCacheProperties properties;

    @Override
    public Optional<AssetMetadataSnapshot> get(UUID workspaceId, UUID assetId) {
        try {
            String value = redisTemplate.opsForValue().get(key(workspaceId, assetId));
            return value == null ? Optional.empty() : Optional.of(objectMapper.readValue(value, AssetMetadataSnapshot.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            log.debug("Asset metadata cache read skipped", exception);
            return Optional.empty();
        }
    }

    @Override
    public void put(AssetMetadataSnapshot response) {
        try {
            redisTemplate.opsForValue().set(
                    key(response.workspaceId(), response.assetId()),
                    objectMapper.writeValueAsString(response),
                    properties.getMetadataTtl()
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            log.debug("Asset metadata cache write skipped", exception);
        }
    }

    @Override
    public void evict(UUID workspaceId, UUID assetId) {
        try {
            redisTemplate.delete(key(workspaceId, assetId));
        } catch (DataAccessException exception) {
            log.debug("Asset metadata cache eviction skipped", exception);
        }
    }

    private String key(UUID workspaceId, UUID assetId) {
        return "%s:%s:%s".formatted(KEY_PREFIX, workspaceId, assetId);
    }
}
