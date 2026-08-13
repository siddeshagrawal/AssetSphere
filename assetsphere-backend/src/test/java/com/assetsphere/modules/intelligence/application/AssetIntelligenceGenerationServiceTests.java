package com.assetsphere.modules.intelligence.application;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.intelligence.api.IntelligenceProcessingLock;
import com.assetsphere.modules.intelligence.api.AiModelDescriptor;
import com.assetsphere.modules.billing.api.Plan;
import java.util.Set;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetIntelligenceGenerationServiceTests {

    @Test
    void authorizesAndRequestsTheExactVersion() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        AssetMetadataSnapshot version = new AssetMetadataSnapshot(
                assetId, UUID.randomUUID(), workspaceId, "report.pdf", "Report", null, AssetType.PDF,
                "application/pdf", 1, "checksum", 2, AssetLifecycleStatus.ACTIVE,
                AssetProcessingStatus.READY, Instant.EPOCH
        );
        WorkspaceAccessFacade access = mock(WorkspaceAccessFacade.class);
        AssetReadFacade assets = mock(AssetReadFacade.class);
        IntelligenceProcessingLock lock = mock(IntelligenceProcessingLock.class);
        IntelligenceProcessingLock.LockHandle handle = mock(IntelligenceProcessingLock.LockHandle.class);
        IntelligenceGenerationTransaction generation = mock(IntelligenceGenerationTransaction.class);
        AiModelCatalogService catalog = mock(AiModelCatalogService.class);
        when(catalog.select(workspaceId, "gpt-4o-mini", "INTELLIGENCE")).thenReturn(
                new AiModelDescriptor("OPENAI", "gpt-4o-mini", "Default", Set.of("INTELLIGENCE"), Plan.FREE, true));
        when(assets.getVersionMetadata(workspaceId, assetId, 2)).thenReturn(version);
        when(lock.tryAcquire(version.assetVersionId())).thenReturn(handle);
        when(handle.acquired()).thenReturn(true);

        new AssetIntelligenceGenerationService(access, assets, lock, generation, catalog)
                .generate(userId, workspaceId, assetId, 2, "gpt-4o-mini");

        verify(access).requireActiveMembership(workspaceId, userId);
        verify(assets).getVersionMetadata(workspaceId, assetId, 2);
        verify(generation).request(version, "gpt-4o-mini");
    }
}
