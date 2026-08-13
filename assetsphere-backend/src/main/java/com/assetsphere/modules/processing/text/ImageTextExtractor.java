package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.io.IOException;
import com.assetsphere.modules.processing.api.ImageOcrProvider;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(90)
class ImageTextExtractor implements TextExtractor {
    private final ObjectProvider<ImageOcrProvider> providers;

    ImageTextExtractor(ObjectProvider<ImageOcrProvider> providers) {
        this.providers = providers;
    }

    @Override
    public boolean supports(AssetProcessingInput input) {
        return input.mimeType() != null && input.mimeType().toLowerCase(java.util.Locale.ROOT).startsWith("image/");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        ImageOcrProvider provider = providers.getIfAvailable();
        if (provider == null) return ExtractionResult.notApplicable("OCR_PROVIDER_DISABLED");
        if (input.fileSize() > provider.maxInputBytes()) throw new PayloadTooLargeException("Image exceeds the configured OCR size limit");
        try {
            return ExtractionResult.extracted(provider.extractText(input, content.readAllBytes()), "IMAGE_OCR");
        } catch (IOException exception) {
            throw new TextExtractionException("Image OCR input could not be read", exception);
        }
    }
}
