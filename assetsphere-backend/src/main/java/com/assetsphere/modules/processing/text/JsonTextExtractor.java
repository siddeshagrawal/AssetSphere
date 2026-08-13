package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(33)
@RequiredArgsConstructor
class JsonTextExtractor implements TextExtractor {

    private static final int MAX_TOKENS = 100_000;
    private static final int MAX_VALUE_CHARACTERS = 4_000;
    private static final int MAX_OUTPUT_CHARACTERS = 1_000_000;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(AssetProcessingInput input) {
        return "application/json".equalsIgnoreCase(input.mimeType())
                || input.originalFilename().toLowerCase(Locale.ROOT).endsWith(".json");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        try (JsonParser parser = objectMapper.getFactory().createParser(content)) {
            StringBuilder output = new StringBuilder();
            String fieldName = null;
            int tokens = 0;
            while (parser.nextToken() != null && tokens++ < MAX_TOKENS && output.length() < MAX_OUTPUT_CHARACTERS) {
                JsonToken token = parser.currentToken();
                if (token == JsonToken.FIELD_NAME) {
                    fieldName = parser.currentName();
                } else if (token.isScalarValue()) {
                    String value = parser.getValueAsString();
                    String line = fieldName == null ? value : fieldName + ": " + value;
                    appendLine(output, line);
                    fieldName = null;
                }
            }
            return ExtractionResult.extracted(output.toString().strip(), "JACKSON");
        } catch (Exception exception) {
            throw new TextExtractionException("JSON text extraction failed", exception);
        }
    }

    private void appendLine(StringBuilder output, String value) {
        if (value == null) return;
        String bounded = value.length() > MAX_VALUE_CHARACTERS ? value.substring(0, MAX_VALUE_CHARACTERS) : value;
        int remaining = MAX_OUTPUT_CHARACTERS - output.length();
        if (remaining <= 0) return;
        String line = bounded + "\n";
        output.append(line, 0, Math.min(line.length(), remaining));
    }
}
