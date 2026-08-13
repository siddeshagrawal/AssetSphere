package com.assetsphere.modules.processing.api;

import com.assetsphere.modules.asset.api.AssetProcessingInput;

public interface MediaTranscriptionProvider {
    default long maxInputBytes() { return Long.MAX_VALUE; }
    String transcribe(AssetProcessingInput input, byte[] media);
}
