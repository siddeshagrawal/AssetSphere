package com.assetsphere.modules.processing.content.persistence;

import com.assetsphere.modules.processing.content.domain.AssetTextContent;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetTextContentRepository extends JpaRepository<AssetTextContent, UUID> {

    Optional<AssetTextContent> findByAssetVersionId(UUID assetVersionId);
}
