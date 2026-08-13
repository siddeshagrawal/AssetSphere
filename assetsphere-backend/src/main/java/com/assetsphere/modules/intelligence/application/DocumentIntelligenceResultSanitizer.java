package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DocumentIntelligenceResultSanitizer {

    private final IntelligenceProperties properties;

    SanitizedIntelligenceResult sanitize(DocumentIntelligenceResult result) {
        if (result == null) {
            throw new BusinessRuleViolationException("Intelligence provider returned no result");
        }
        return new SanitizedIntelligenceResult(
                boundedRequired(result.summary(), properties.getMaxSummaryCharacters(), "summary"),
                boundedItems(result.keyPoints(), properties.getMaxKeyPoints(), properties.getMaxKeyPointCharacters(), "key point"),
                boundedItems(result.tags(), properties.getMaxTags(), properties.getMaxTagCharacters(), "tag")
        );
    }

    private String boundedRequired(String value, int maximum, String field) {
        if (maximum < 1 || value == null || value.isBlank()) {
            throw new BusinessRuleViolationException("Intelligence " + field + " is invalid");
        }
        String normalized = value.strip();
        if (normalized.length() > maximum) {
            throw new BusinessRuleViolationException("Intelligence " + field + " is too long");
        }
        return normalized;
    }

    private List<String> boundedItems(List<String> values, int maximumCount, int maximumLength, String field) {
        if (maximumCount < 0 || maximumLength < 1 || values == null) {
            throw new BusinessRuleViolationException("Intelligence " + field + " list is invalid");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String item = value.strip();
            if (item.length() > maximumLength) {
                throw new BusinessRuleViolationException("Intelligence " + field + " is too long");
            }
            normalized.add(item);
        }
        return normalized.stream().limit(maximumCount).toList();
    }
}
