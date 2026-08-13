package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;

/**
 * Receives the versioned Processing event after an asset's deterministic processing is complete.
 */
public interface AssetReadyForIntelligenceFacade {

    void process(AssetReadyForIntelligenceEvent event);

    void markFailed(AssetReadyForIntelligenceEvent event, String failureCode);
}
