package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.AssetEvolutionResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssetEvolutionResultSanitizer {
    private final IntelligenceProperties properties;

    AssetEvolutionResult sanitize(AssetEvolutionResult result) {
        if (result == null || result.executiveSummary() == null || result.executiveSummary().isBlank()) {
            throw new BusinessRuleViolationException("Evolution provider returned an invalid result");
        }
        String summary = result.executiveSummary().strip();
        if (summary.length() > properties.getMaxSummaryCharacters()) {
            throw new BusinessRuleViolationException("Evolution summary is too long");
        }
        return new AssetEvolutionResult(summary, items(result.keyChanges()), items(result.additions()),
                items(result.removals()), items(result.importantChanges()));
    }

    private List<String> items(List<String> values) {
        if (values == null) throw new BusinessRuleViolationException("Evolution change list is invalid");
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) continue;
            String item = value.strip();
            if (item.length() > properties.getMaxKeyPointCharacters()) {
                throw new BusinessRuleViolationException("Evolution change item is too long");
            }
            normalized.add(item);
        }
        return normalized.stream().limit(properties.getMaxKeyPoints()).toList();
    }
}
