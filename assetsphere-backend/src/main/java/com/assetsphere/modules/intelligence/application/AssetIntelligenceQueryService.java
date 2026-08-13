package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.intelligence.api.dto.response.AssetIntelligenceResponse;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetIntelligenceQueryService {

    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetReadFacade assets;
    private final AssetIntelligenceRepository intelligences;
    private final AssetIntelligenceResponseMapper responseMapper;

    @Transactional(readOnly = true)
    public AssetIntelligenceResponse get(UUID userId, UUID workspaceId, UUID assetId) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        var asset = assets.getMetadata(workspaceId, assetId);
        return intelligences.findByAssetVersionId(asset.assetVersionId())
                .map(responseMapper::from)
                .orElseGet(() -> responseMapper.notGenerated(asset.assetId(), asset.assetVersionId()));
    }

    @Transactional(readOnly = true)
    public AssetIntelligenceResponse getVersion(UUID userId, UUID workspaceId, UUID assetId, int versionNumber) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        var asset = assets.getVersionMetadata(workspaceId, assetId, versionNumber);
        return intelligences.findByAssetVersionId(asset.assetVersionId())
                .map(responseMapper::from)
                .orElseGet(() -> responseMapper.notGenerated(asset.assetId(), asset.assetVersionId()));
    }
}
