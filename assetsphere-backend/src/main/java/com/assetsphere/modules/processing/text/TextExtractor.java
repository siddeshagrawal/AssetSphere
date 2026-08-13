package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;

public interface TextExtractor {

    boolean supports(AssetProcessingInput input);

    ExtractionResult extract(AssetProcessingInput input, InputStream content);
}
