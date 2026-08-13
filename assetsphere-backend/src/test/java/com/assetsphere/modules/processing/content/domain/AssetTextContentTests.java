package com.assetsphere.modules.processing.content.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.processing.text.ExtractionResult;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetTextContentTests {

    @Test
    void removesOnlyNulCharactersFromExtractedText() {
        AssetTextContent content = AssetTextContent.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                ExtractionResult.extracted("Asset\u0000Sphere — 東京", "TEST"));

        assertThat(content.getExtractedText()).isEqualTo("AssetSphere — 東京");
        assertThat(content.getCharacterCount()).isEqualTo("AssetSphere — 東京".length());
    }
}
