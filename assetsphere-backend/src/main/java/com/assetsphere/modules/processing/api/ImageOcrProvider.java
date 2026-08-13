package com.assetsphere.modules.processing.api;

import com.assetsphere.modules.asset.api.AssetProcessingInput;

public interface ImageOcrProvider {
    default long maxInputBytes() { return Long.MAX_VALUE; }
    String extractText(AssetProcessingInput input, byte[] image);
}
