package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.api.AssetMetadataUpdatedEvent;
import com.assetsphere.modules.asset.api.dto.request.UpdateAssetMetadataRequest;
import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetMetadataApplicationService {
    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetRepository assets;
    private final AssetVersionRepository versions;
    private final AssetMetadataCache cache;
    private final ApplicationEventPublisher events;

    @Transactional
    public AssetResponse update(UUID userId, UUID workspaceId, UUID assetId, UpdateAssetMetadataRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        var asset = assets.findByIdAndWorkspaceId(assetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        asset.updateMetadata(request.displayName(), request.description());
        var version = versions.findByAssetIdAndVersionNumber(assetId, asset.getLatestVersionNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Asset version not found"));
        events.publishEvent(new AssetMetadataUpdatedEvent(
                workspaceId, assetId, asset.getDisplayName(), asset.getDescription()
        ));
        cache.evict(workspaceId, assetId);
        return AssetResponse.from(asset, version);
    }
}
