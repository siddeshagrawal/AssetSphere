package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import org.junit.jupiter.api.Test;

class IntelligenceInputBounderTests {

    @Test
    void usesHeadAndTailWhenContentExceedsBound() {
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setMaxInputCharacters(80);
        BoundedIntelligenceInput bounded = new IntelligenceInputBounder(properties).bound("a".repeat(50) + "middle" + "z".repeat(50));
        assertThat(bounded.truncated()).isTrue();
        assertThat(bounded.content()).hasSizeLessThanOrEqualTo(80).contains("document content truncated");
    }

    @Test
    void rejectsEmptyInput() {
        assertThatThrownBy(() -> new IntelligenceInputBounder(new IntelligenceProperties()).bound("  "))
                .isInstanceOf(BusinessRuleViolationException.class);
    }
}
