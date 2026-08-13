package com.assetsphere.modules.billing.api;

import java.util.UUID;

public interface WorkspaceResourceUsageProvider {
    WorkspaceResourceUsage usage(UUID workspaceId);
    record WorkspaceResourceUsage(long assets, long storageBytes) { }
}
