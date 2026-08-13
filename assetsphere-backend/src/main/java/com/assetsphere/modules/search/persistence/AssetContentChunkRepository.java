package com.assetsphere.modules.search.persistence;

import com.assetsphere.modules.search.domain.AssetContentChunk;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetContentChunkRepository extends JpaRepository<AssetContentChunk, UUID> {
    List<AssetContentChunk> findByAssetVersionIdOrderByChunkIndex(UUID assetVersionId);
    void deleteByAssetVersionId(UUID assetVersionId);
}
