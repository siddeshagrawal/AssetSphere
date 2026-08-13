package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(30)
class PlainTextExtractor implements TextExtractor {

    @Override
    public boolean supports(AssetProcessingInput input) {
        String filename = input.originalFilename().toLowerCase(Locale.ROOT);
        return filename.endsWith(".txt") || ("text/plain".equalsIgnoreCase(input.mimeType()) && !filename.endsWith(".md"));
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        return ExtractionResult.extracted(SafeTextDecoder.decode(content, "TXT"), "PLAIN_TEXT");
    }
}
