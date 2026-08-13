package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.billing.api.WorkspaceResourceUsageProvider;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssetWorkspaceResourceUsageProvider implements WorkspaceResourceUsageProvider {
    private final AssetRepository assets;
    private final AssetVersionRepository versions;

    @Override
    public WorkspaceResourceUsage usage(UUID workspaceId) {
        return new WorkspaceResourceUsage(assets.countByWorkspaceIdAndDeletedAtIsNull(workspaceId),
                versions.sumFileSizeByWorkspaceId(workspaceId));
    }
}
