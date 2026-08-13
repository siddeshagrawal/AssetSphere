package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.intelligence.api.dto.response.AssetIntelligenceResponse;
import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import com.assetsphere.modules.intelligence.domain.IntelligenceStatus;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IntelligenceGenerationTransaction {
    private final AssetIntelligenceRepository intelligences;
    private final ApplicationEventPublisher eventPublisher;
    private final AssetIntelligenceResponseMapper responseMapper;
    private final ClockProvider clock;
    private final BillingEntitlementFacade billing;

    @Transactional
    AssetIntelligenceResponse request(AssetMetadataSnapshot asset) { return request(asset, null); }

    @Transactional
    AssetIntelligenceResponse request(AssetMetadataSnapshot asset, String modelId) {
        AssetIntelligence intelligence = intelligences.findByAssetVersionId(asset.assetVersionId()).orElse(null);
        boolean publish = false;
        if (intelligence == null) {
            intelligence = intelligences.save(AssetIntelligence.requested(
                    asset.workspaceId(), asset.assetId(), asset.assetVersionId(), clock.now(), modelId
            ));
            publish = true;
        } else if (intelligence.getStatus() == IntelligenceStatus.FAILED) {
            intelligence.retry(clock.now(), modelId);
            publish = true;
        }
        if (publish) {
            billing.requireAvailable(asset.workspaceId(), UsageMetric.AI_INSIGHT);
            eventPublisher.publishEvent(AssetReadyForIntelligenceEvent.create(
                    asset.assetId(), asset.assetVersionId(), asset.workspaceId(), clock.now()
            ));
        }
        return responseMapper.from(intelligence);
    }

    @Transactional(readOnly = true)
    AssetIntelligenceResponse current(AssetMetadataSnapshot asset) {
        return intelligences.findByAssetVersionId(asset.assetVersionId())
                .map(responseMapper::from)
                .orElseGet(() -> responseMapper.notGenerated(asset.assetId(), asset.assetVersionId()));
    }
}
