package com.assetsphere.modules.billing.api;

import java.util.UUID;

public interface BillingEntitlementFacade {
    PlanEntitlements entitlements(UUID workspaceId);
    void requireAssetUpload(UUID workspaceId, long additionalBytes);
    void requireStorage(UUID workspaceId, long additionalBytes);
    void requireAvailable(UUID workspaceId, UsageMetric metric);
    long consume(UUID workspaceId, UsageMetric metric);
    long consumeOnce(UUID workspaceId, UsageMetric metric, UUID operationId);
}
