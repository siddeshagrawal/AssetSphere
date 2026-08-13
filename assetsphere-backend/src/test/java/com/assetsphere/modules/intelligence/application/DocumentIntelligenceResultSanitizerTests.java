package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentIntelligenceResultSanitizerTests {

    @Test
    void normalizesValidStructuredOutput() {
        SanitizedIntelligenceResult result = sanitizer().sanitize(
                new DocumentIntelligenceResult(" Summary ", List.of(" Point ", "Point"), List.of("tag", "tag"))
        );
        assertThat(result.summary()).isEqualTo("Summary");
        assertThat(result.keyPoints()).containsExactly("Point");
        assertThat(result.tags()).containsExactly("tag");
    }

    @Test
    void truncatesOverLimitCollectionsWhilePreservingNormalizedOrder() {
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setMaxKeyPoints(2);
        properties.setMaxTags(2);

        SanitizedIntelligenceResult result = new DocumentIntelligenceResultSanitizer(properties).sanitize(
                new DocumentIntelligenceResult("Summary",
                        List.of(" First ", "", "Second", "First", "Third"),
                        List.of("beta", " alpha ", "beta", "gamma")));

        assertThat(result.keyPoints()).containsExactly("First", "Second");
        assertThat(result.tags()).containsExactly("beta", "alpha");
    }

    @Test
    void stillRejectsMalformedOverlengthItems() {
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setMaxTagCharacters(3);

        assertThatThrownBy(() -> new DocumentIntelligenceResultSanitizer(properties).sanitize(
                new DocumentIntelligenceResult("Summary", List.of(), List.of("valid-length"))))
                .isInstanceOf(BusinessRuleViolationException.class);
    }

    private DocumentIntelligenceResultSanitizer sanitizer() {
        return new DocumentIntelligenceResultSanitizer(new IntelligenceProperties());
    }
}
