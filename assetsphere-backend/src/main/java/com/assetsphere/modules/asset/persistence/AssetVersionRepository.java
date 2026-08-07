package com.assetsphere.modules.asset.persistence;

import com.assetsphere.modules.asset.domain.AssetVersion;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetVersionRepository extends JpaRepository<AssetVersion, UUID> {

    Optional<AssetVersion> findByAssetIdAndVersionNumber(UUID assetId, int versionNumber);
}
