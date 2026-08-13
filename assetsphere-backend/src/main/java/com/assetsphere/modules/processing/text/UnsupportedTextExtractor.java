package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1000)
class UnsupportedTextExtractor implements TextExtractor {

    @Override
    public boolean supports(AssetProcessingInput input) {
        return true;
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        return ExtractionResult.unsupported();
    }
}
