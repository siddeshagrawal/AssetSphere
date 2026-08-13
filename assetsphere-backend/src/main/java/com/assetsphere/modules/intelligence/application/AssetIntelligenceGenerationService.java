package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.IntelligenceProcessingLock;
import com.assetsphere.modules.intelligence.api.dto.response.AssetIntelligenceResponse;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetIntelligenceGenerationService {
    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetReadFacade assets;
    private final IntelligenceProcessingLock lock;
    private final IntelligenceGenerationTransaction generation;
    private final AiModelCatalogService modelCatalog;

    public AssetIntelligenceResponse generate(UUID userId, UUID workspaceId, UUID assetId, int versionNumber, String modelId) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        AssetMetadataSnapshot asset = assets.getVersionMetadata(workspaceId, assetId, versionNumber);
        if (!asset.readyForIntelligence()) {
            throw new BusinessRuleViolationException("Asset version must finish processing before intelligence generation");
        }
        String selectedModel = modelCatalog.select(workspaceId, modelId, "INTELLIGENCE").modelId();
        try (IntelligenceProcessingLock.LockHandle handle = lock.tryAcquire(asset.assetVersionId())) {
            return handle.acquired() ? generation.request(asset, selectedModel) : generation.current(asset);
        }
    }
}
