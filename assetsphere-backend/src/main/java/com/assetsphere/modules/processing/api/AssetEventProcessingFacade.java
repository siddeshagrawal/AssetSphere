package com.assetsphere.modules.processing.api;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;

public interface AssetEventProcessingFacade {

    void process(AssetUploadedEvent event);

    void markFailed(AssetUploadedEvent event);
}
