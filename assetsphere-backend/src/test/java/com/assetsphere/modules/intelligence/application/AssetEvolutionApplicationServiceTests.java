package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.intelligence.api.AssetEvolutionModel;
import com.assetsphere.modules.intelligence.api.AssetEvolutionRequest;
import com.assetsphere.modules.intelligence.api.AssetEvolutionResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.dto.request.CompareAssetVersionsRequest;
import com.assetsphere.modules.processing.api.ProcessedContent;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.intelligence.api.AiModelDescriptor;
import java.util.Set;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;

class AssetEvolutionApplicationServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void authorizesBoundsAndComparesTheTwoExactVersionsOnce() {
        UUID userId = UUID.randomUUID(), workspaceId = UUID.randomUUID(), assetId = UUID.randomUUID();
        AssetMetadataSnapshot from = version(workspaceId, assetId, UUID.randomUUID(), 1);
        AssetMetadataSnapshot to = version(workspaceId, assetId, UUID.randomUUID(), 2);
        WorkspaceAccessFacade access = mock(WorkspaceAccessFacade.class);
        AssetReadFacade assets = mock(AssetReadFacade.class);
        ProcessedContentFacade contents = mock(ProcessedContentFacade.class);
        ObjectProvider<AssetEvolutionModel> models = mock(ObjectProvider.class);
        AssetEvolutionModel model = mock(AssetEvolutionModel.class);
        BillingEntitlementFacade billing = mock(BillingEntitlementFacade.class);
        AiModelCatalogService catalog = mock(AiModelCatalogService.class);
        when(catalog.select(workspaceId, null, "EVOLUTION")).thenReturn(
                new AiModelDescriptor("OPENAI", "gpt-4o-mini", "Default", Set.of("EVOLUTION"), Plan.FREE, true));
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setMaxInputCharacters(20);
        when(assets.getVersionMetadata(workspaceId, assetId, 1)).thenReturn(from);
        when(assets.getVersionMetadata(workspaceId, assetId, 2)).thenReturn(to);
        when(contents.findByAssetVersionId(from.assetVersionId())).thenReturn(Optional.of(content(from, "a".repeat(30))));
        when(contents.findByAssetVersionId(to.assetVersionId())).thenReturn(Optional.of(content(to, "b".repeat(30))));
        when(models.getIfAvailable()).thenReturn(model);
        when(model.compare(any())).thenReturn(new AssetEvolutionResult("Changed", List.of(), List.of(), List.of(), List.of()));
        var service = new AssetEvolutionApplicationService(access, assets, contents,
                new IntelligenceInputBounder(properties), properties, models,
                new AssetEvolutionResultSanitizer(properties), billing, catalog);

        var response = service.compare(userId, workspaceId, assetId, new CompareAssetVersionsRequest(1, 2, null));

        verify(access).requireActiveMembership(workspaceId, userId);
        ArgumentCaptor<AssetEvolutionRequest> request = ArgumentCaptor.forClass(AssetEvolutionRequest.class);
        verify(model).compare(request.capture());
        assertThat(request.getValue().modelId()).isEqualTo("gpt-4o-mini");
        assertThat(request.getValue().fromContent()).hasSizeLessThanOrEqualTo(10);
        assertThat(request.getValue().toContent()).hasSizeLessThanOrEqualTo(10);
        assertThat(response.fromVersion()).isEqualTo(1);
        assertThat(response.toVersion()).isEqualTo(2);
        verify(billing).consume(workspaceId, UsageMetric.EVOLUTION);
    }

    private AssetMetadataSnapshot version(UUID workspaceId, UUID assetId, UUID versionId, int number) {
        return new AssetMetadataSnapshot(assetId, versionId, workspaceId, "report.pdf", "Report", null,
                AssetType.PDF, "application/pdf", 1, "checksum", number, AssetLifecycleStatus.ACTIVE,
                AssetProcessingStatus.READY, Instant.EPOCH);
    }

    private ProcessedContent content(AssetMetadataSnapshot version, String text) {
        return new ProcessedContent(version.workspaceId(), version.assetId(), version.assetVersionId(), text, "EXTRACTED", false);
    }
}
