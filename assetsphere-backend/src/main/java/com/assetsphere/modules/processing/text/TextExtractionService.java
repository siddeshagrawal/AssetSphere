package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import java.io.InputStream;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TextExtractionService {

    private final List<TextExtractor> extractors;
    private final ProcessingProperties properties;

    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        if (input.fileSize() > properties.getMaxProcessingFileSize().toBytes()) {
            throw new BusinessRuleViolationException("Asset exceeds the configured processing size limit");
        }
        ExtractionResult result = extractors.stream()
                .filter(extractor -> extractor.supports(input))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No text extractor is registered"))
                .extract(input, content);
        String normalized = normalize(result.text());
        boolean truncated = normalized.length() > properties.getMaxRetainedTextCharacters();
        String retained = truncated ? normalized.substring(0, properties.getMaxRetainedTextCharacters()) : normalized;
        return result.withNormalizedText(retained, truncated);
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("[\\t\\x0B\\f ]+", " ")
                .replaceAll("\\r?\\n[ \\t]*", "\n")
                .trim();
    }
}
