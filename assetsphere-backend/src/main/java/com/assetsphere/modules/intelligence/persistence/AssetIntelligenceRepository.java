package com.assetsphere.modules.intelligence.persistence;

import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetIntelligenceRepository extends JpaRepository<AssetIntelligence, UUID> {

    Optional<AssetIntelligence> findByAssetVersionId(UUID assetVersionId);

    List<AssetIntelligence> findByWorkspaceIdAndAssetIdOrderByCreatedAtDesc(UUID workspaceId, UUID assetId);
}
