package com.assetsphere.infrastructure.search;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class ProductionEmbeddingConfigurationTests {
    @Test
    void productionAiEnablesOpenAiEmbeddingAdapterByDefault() throws IOException {
        PropertySourcesPropertyResolver properties = productionProperties(Map.of(
                "ASSETSPHERE_AI_ENABLED", "true",
                "ASSETSPHERE_AI_SPRING_EMBEDDING_MODEL", "openai"));

        assertThat(properties.getProperty("assetsphere.ai.embedding.enabled", Boolean.class)).isTrue();
        assertThat(properties.getProperty("spring.ai.model.embedding")).isEqualTo("openai");
        assertThat(properties.getProperty("assetsphere.ai.embedding.model"))
                .isEqualTo("text-embedding-3-small");
        assertThat(properties.getProperty("assetsphere.ai.embedding.dimension", Integer.class)).isEqualTo(1536);
    }

    @Test
    void explicitEmbeddingDisableOverridesEnabledAi() throws IOException {
        PropertySourcesPropertyResolver properties = productionProperties(Map.of(
                "ASSETSPHERE_AI_ENABLED", "true",
                "ASSETSPHERE_AI_EMBEDDING_ENABLED", "false",
                "ASSETSPHERE_AI_SPRING_EMBEDDING_MODEL", "openai"));

        assertThat(properties.getProperty("assetsphere.ai.embedding.enabled", Boolean.class)).isFalse();
    }

    @Test
    void disabledAiKeepsEmbeddingAdapterDisabled() throws IOException {
        PropertySourcesPropertyResolver properties = productionProperties(Map.of(
                "ASSETSPHERE_AI_ENABLED", "false"));

        assertThat(properties.getProperty("assetsphere.ai.embedding.enabled", Boolean.class)).isFalse();
    }

    private PropertySourcesPropertyResolver productionProperties(Map<String, Object> environment)
            throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addFirst(new MapPropertySource("test-environment", new LinkedHashMap<>(environment)));
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load("application-prod", new ClassPathResource("application-prod.yml"))
                .forEach(sources::addLast);
        loader.load("application", new ClassPathResource("application.yml"))
                .forEach(sources::addLast);
        return new PropertySourcesPropertyResolver(sources);
    }
}
