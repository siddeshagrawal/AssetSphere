package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.AssetEvolutionModel;
import com.assetsphere.modules.intelligence.api.AssetEvolutionRequest;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.IntelligenceProviderException;
import com.assetsphere.modules.intelligence.api.dto.request.CompareAssetVersionsRequest;
import com.assetsphere.modules.intelligence.api.dto.response.AssetEvolutionResponse;
import com.assetsphere.modules.processing.api.ProcessedContent;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetEvolutionApplicationService {
    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetReadFacade assets;
    private final ProcessedContentFacade processedContents;
    private final IntelligenceInputBounder inputBounder;
    private final IntelligenceProperties properties;
    private final ObjectProvider<AssetEvolutionModel> model;
    private final AssetEvolutionResultSanitizer sanitizer;
    private final BillingEntitlementFacade billing;
    private final AiModelCatalogService modelCatalog;

    public AssetEvolutionResponse compare(UUID userId, UUID workspaceId, UUID assetId, CompareAssetVersionsRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        if (request.fromVersion() == request.toVersion()) {
            throw new BusinessRuleViolationException("Comparison versions must be different");
        }
        var from = assets.getVersionMetadata(workspaceId, assetId, request.fromVersion());
        var to = assets.getVersionMetadata(workspaceId, assetId, request.toVersion());
        ProcessedContent fromContent = content(from.assetVersionId(), workspaceId, assetId);
        ProcessedContent toContent = content(to.assetVersionId(), workspaceId, assetId);
        int perVersionLimit = Math.max(1, properties.getMaxInputCharacters() / 2);
        var boundedFrom = inputBounder.bound(fromContent.extractedText(), perVersionLimit);
        var boundedTo = inputBounder.bound(toContent.extractedText(), perVersionLimit);
        AssetEvolutionModel provider = model.getIfAvailable();
        if (provider == null) {
            throw IntelligenceProviderException.nonRetryable("No configured evolution intelligence provider", null);
        }
        String selectedModel = modelCatalog.select(workspaceId, request.modelId(), "EVOLUTION").modelId();
        billing.consume(workspaceId, UsageMetric.EVOLUTION);
        var result = sanitizer.sanitize(provider.compare(new AssetEvolutionRequest(
                selectedModel,
                from.versionNumber(), from.originalFilename(), from.mimeType(), boundedFrom.content(),
                to.versionNumber(), to.originalFilename(), to.mimeType(), boundedTo.content()
        )));
        return new AssetEvolutionResponse(from.versionNumber(), to.versionNumber(), result.executiveSummary(),
                result.keyChanges(), result.additions(), result.removals(), result.importantChanges());
    }

    private ProcessedContent content(UUID versionId, UUID workspaceId, UUID assetId) {
        ProcessedContent content = processedContents.findByAssetVersionId(versionId)
                .orElseThrow(() -> new BusinessRuleViolationException("Processed version content is not available"));
        if (!content.workspaceId().equals(workspaceId) || !content.assetId().equals(assetId) || !content.hasUsableText()) {
            throw new BusinessRuleViolationException("Processed version content is not available");
        }
        return content;
    }
}
