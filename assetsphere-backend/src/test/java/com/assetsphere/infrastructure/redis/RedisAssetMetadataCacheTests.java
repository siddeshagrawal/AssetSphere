package com.assetsphere.infrastructure.redis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetCacheProperties;
import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAssetMetadataCacheTests {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final RedisAssetMetadataCache cache = new RedisAssetMetadataCache(
            redisTemplate,
            new ObjectMapper().registerModule(new JavaTimeModule()),
            new AssetCacheProperties()
    );

    @Test
    void treatsMalformedCachedMetadataAsCacheMiss() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get("assetsphere:cache:asset-metadata:%s:%s".formatted(workspaceId, assetId)))
                .thenReturn("not-json");

        assertThat(cache.get(workspaceId, assetId)).isEmpty();
    }

    @Test
    void writesTypedMetadataUsingNamespacedKey() {
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        when(redisTemplate.opsForValue()).thenReturn(values);
        AssetMetadataSnapshot snapshot = new AssetMetadataSnapshot(
                assetId,
                UUID.randomUUID(),
                workspaceId,
                "report.pdf",
                "Report",
                null,
                AssetType.PDF,
                "application/pdf",
                128,
                "checksum",
                1,
                AssetLifecycleStatus.ACTIVE,
                AssetProcessingStatus.READY,
                Instant.parse("2026-08-07T00:00:00Z")
        );

        cache.put(snapshot);

        verify(values).set(
                eq("assetsphere:cache:asset-metadata:%s:%s".formatted(workspaceId, assetId)),
                any(String.class),
                eq(new AssetCacheProperties().getMetadataTtl())
        );
    }
}
