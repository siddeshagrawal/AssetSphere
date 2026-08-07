package com.assetsphere.modules.asset.persistence;

import com.assetsphere.modules.asset.domain.Asset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRepository extends JpaRepository<Asset, UUID> {

    Optional<Asset> findByIdAndWorkspaceId(UUID id, UUID workspaceId);
}
