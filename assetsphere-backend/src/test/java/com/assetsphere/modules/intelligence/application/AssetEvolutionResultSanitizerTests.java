package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.intelligence.api.AssetEvolutionResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssetEvolutionResultSanitizerTests {

    @Test
    void boundsDeduplicatesAndPreservesChangeOrder() {
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setMaxKeyPoints(2);
        AssetEvolutionResultSanitizer sanitizer = new AssetEvolutionResultSanitizer(properties);

        AssetEvolutionResult result = sanitizer.sanitize(new AssetEvolutionResult(
                "  The policy evolved.  ", List.of("First", "First", "Second", "Third"),
                List.of("Added"), List.of("Removed"), List.of("Important")
        ));

        assertThat(result.executiveSummary()).isEqualTo("The policy evolved.");
        assertThat(result.keyChanges()).containsExactly("First", "Second");
    }
}
