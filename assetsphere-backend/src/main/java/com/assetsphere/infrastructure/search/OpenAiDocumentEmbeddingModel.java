package com.assetsphere.infrastructure.search;

import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "assetsphere.ai.embedding", name = "enabled", havingValue = "true")
class OpenAiDocumentEmbeddingModel implements EmbeddingModelPort {

    private final ObjectProvider<EmbeddingModel> models;
    private final SemanticIndexProperties properties;

    @Override
    public List<EmbeddingVector> embed(List<String> inputs) {
        EmbeddingModel model = models.getIfAvailable();
        if (model == null) throw new IllegalStateException("OpenAI embedding provider is not configured");
        try {
            List<EmbeddingVector> vectors = model.embed(inputs).stream().map(EmbeddingVector::new).toList();
            if (vectors.size() != inputs.size() || vectors.stream().anyMatch(vector -> vector.values().length != properties.getDimension())) {
                throw new IllegalStateException("OpenAI embedding dimension does not match configured semantic schema");
            }
            return vectors;
        } catch (ResourceAccessException exception) {
            throw new IllegalStateException("OpenAI embedding provider is temporarily unavailable", exception);
        }
    }
}
