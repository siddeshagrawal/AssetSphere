package com.assetsphere.modules.search.persistence;

import com.assetsphere.modules.search.domain.AssetSemanticIndex;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetSemanticIndexRepository extends JpaRepository<AssetSemanticIndex, UUID> {
    Optional<AssetSemanticIndex> findByAssetVersionId(UUID assetVersionId);
}
