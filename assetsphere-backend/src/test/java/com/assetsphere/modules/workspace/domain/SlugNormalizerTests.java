package com.assetsphere.modules.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SlugNormalizerTests {
    @Test
    void normalizesNamesToStableLowercaseSlugs() {
        assertThat(SlugNormalizer.normalize("  Product / Design Team  ")).isEqualTo("product-design-team");
    }

    @Test
    void rejectsTooShortNormalizedSlug() {
        assertThatThrownBy(() -> SlugNormalizer.normalize("--"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
