package com.assetsphere.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class OpenAiDocumentEmbeddingModelWiringTests {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(EmbeddingAdapterConfiguration.class)
            .withBean(EmbeddingModel.class, () -> mock(EmbeddingModel.class))
            .withBean(SemanticIndexProperties.class, SemanticIndexProperties::new);

    @Test
    void enabledEmbeddingExposesSpringAiModelThroughSearchPort() {
        contextRunner.withPropertyValues("assetsphere.ai.embedding.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(EmbeddingModelPort.class);
                    assertThat(context.getBeanProvider(EmbeddingModelPort.class).getIfAvailable())
                            .isInstanceOf(OpenAiDocumentEmbeddingModel.class);
                });
    }

    @Test
    void disabledEmbeddingLeavesSearchWithoutProvider() {
        contextRunner.withPropertyValues("assetsphere.ai.embedding.enabled=false")
                .run(context -> assertThat(context).doesNotHaveBean(EmbeddingModelPort.class));
    }

    @Test
    void semanticConfigurationRemainsSchemaCompatible() {
        SemanticIndexProperties properties = new SemanticIndexProperties();

        assertThat(properties.getModel()).isEqualTo("text-embedding-3-small");
        assertThat(properties.getDimension()).isEqualTo(1536);
    }

    @Configuration(proxyBeanMethods = false)
    @Import(OpenAiDocumentEmbeddingModel.class)
    static class EmbeddingAdapterConfiguration { }
}
