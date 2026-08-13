package com.assetsphere.modules.asset.persistence;

import com.assetsphere.modules.asset.domain.AssetVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    Optional<AssetVersion> findByAssetIdAndVersionNumber(UUID assetId, int versionNumber);

    List<AssetVersion> findByAssetIdIn(List<UUID> assetIds);

    List<AssetVersion> findByAssetIdOrderByVersionNumberDesc(UUID assetId);

    @Query("""
            select coalesce(sum(version.fileSize), 0) from AssetVersion version, Asset asset
             where version.assetId = asset.id and asset.workspaceId = :workspaceId and asset.deletedAt is null
            """)
    long sumFileSizeByWorkspaceId(@Param("workspaceId") UUID workspaceId);
}
