package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceRequest;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import com.assetsphere.modules.intelligence.domain.IntelligenceStatus;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import com.assetsphere.modules.processing.api.ProcessedContent;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IntelligencePreparationTransaction {

    private final AssetIntelligenceRepository intelligences;
    private final ProcessedContentFacade processedContent;
    private final AssetReadFacade assets;
    private final IntelligenceInputBounder inputBounder;
    private final IntelligenceProperties properties;
    private final ClockProvider clock;
    private final AiModelCatalogService modelCatalog;

    @Transactional
    public IntelligencePreparedWork prepare(AssetReadyForIntelligenceEvent event) {
        AssetIntelligence intelligence = intelligences.findByAssetVersionId(event.assetVersionId())
                .orElseGet(() -> intelligences.save(AssetIntelligence.pending(
                        event.workspaceId(), event.assetId(), event.assetVersionId()
                )));
        assertScope(intelligence, event);
        if (intelligence.isTerminal()) {
            return IntelligencePreparedWork.skipped();
        }
        if (!properties.isEnabled()) {
            intelligence.disable(clock.now());
            return IntelligencePreparedWork.skipped();
        }
        ProcessedContent content = processedContent.findByAssetVersionId(event.assetVersionId())
                .orElseThrow(() -> new BusinessRuleViolationException("Processed content is not available"));
        assertScope(content, event);
        if (!content.hasUsableText()) {
            intelligence.markNotApplicable(clock.now());
            return IntelligencePreparedWork.skipped();
        }
        AssetMetadataSnapshot asset = assets.getVersionMetadata(
                event.workspaceId(), event.assetId(), event.assetVersionId()
        );
        BoundedIntelligenceInput bounded = inputBounder.bound(content.extractedText());
        String selectedModel = modelCatalog.select(event.workspaceId(), intelligence.getRequestedModelId(), "INTELLIGENCE").modelId();
        if (intelligence.getStatus() == IntelligenceStatus.PENDING || intelligence.getStatus() == IntelligenceStatus.PROCESSING) {
            intelligence.start(properties.getProvider(), selectedModel, bounded.content().length(), bounded.truncated(), clock.now());
        }
        return new IntelligencePreparedWork(new DocumentIntelligenceRequest(
                event.assetId(), event.assetVersionId(), event.workspaceId(), selectedModel, asset.originalFilename(), asset.mimeType(),
                bounded.content(), bounded.truncated()
        ));
    }

    private void assertScope(AssetIntelligence intelligence, AssetReadyForIntelligenceEvent event) {
        if (!intelligence.getWorkspaceId().equals(event.workspaceId()) || !intelligence.getAssetId().equals(event.assetId())) {
            throw new BusinessRuleViolationException("Intelligence event scope is invalid");
        }
    }

    private void assertScope(ProcessedContent content, AssetReadyForIntelligenceEvent event) {
        if (!content.workspaceId().equals(event.workspaceId()) || !content.assetId().equals(event.assetId())) {
            throw new BusinessRuleViolationException("Processed content scope is invalid");
        }
    }
}
