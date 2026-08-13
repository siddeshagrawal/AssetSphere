package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class IntelligenceInputBounder {

    private static final String TRUNCATION_MARKER = "\n\n[... document content truncated ...]\n\n";

    private final IntelligenceProperties properties;

    BoundedIntelligenceInput bound(String extractedText) {
        return bound(extractedText, properties.getMaxInputCharacters());
    }

    BoundedIntelligenceInput bound(String extractedText, int maximum) {
        if (extractedText == null || extractedText.isBlank()) {
            throw new BusinessRuleViolationException("Intelligence content is empty");
        }
        String content = extractedText.strip();
        if (maximum < 1) {
            throw new BusinessRuleViolationException("Intelligence maximum input characters must be positive");
        }
        if (content.length() <= maximum) {
            return new BoundedIntelligenceInput(content, false);
        }
        if (maximum <= TRUNCATION_MARKER.length()) {
            return new BoundedIntelligenceInput(content.substring(0, maximum), true);
        }
        int remainingCharacters = maximum - TRUNCATION_MARKER.length();
        int headLength = remainingCharacters / 2;
        int tailLength = remainingCharacters - headLength;
        return new BoundedIntelligenceInput(
                content.substring(0, headLength) + TRUNCATION_MARKER
                        + content.substring(content.length() - tailLength),
                true
        );
    }
}
