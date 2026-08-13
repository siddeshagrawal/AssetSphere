package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(31)
class MarkdownTextExtractor implements TextExtractor {

    @Override
    public boolean supports(AssetProcessingInput input) {
        return "text/markdown".equalsIgnoreCase(input.mimeType())
                || input.originalFilename().toLowerCase(Locale.ROOT).endsWith(".md");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        return ExtractionResult.extracted(SafeTextDecoder.decode(content, "Markdown"), "MARKDOWN");
    }
}
