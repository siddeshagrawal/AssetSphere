package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.api.SemanticIndexingException;
import com.assetsphere.modules.search.api.SemanticIndexingFacade;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssetReadyForSemanticIndexKafkaListener {
    private final ObjectMapper objectMapper; private final SemanticIndexingFacade indexing;
    @KafkaListener(topics = "${assetsphere.ai.embedding.topics.asset-ready}", groupId = "${assetsphere.ai.embedding.consumer.group-id}", containerFactory = "semanticKafkaListenerContainerFactory")
    void consume(String payload) {
        try { AssetReadyForSemanticIndexEvent event = objectMapper.readValue(payload, AssetReadyForSemanticIndexEvent.class); indexing.process(new SemanticIndexRequest(event.workspaceId(), event.assetId(), event.assetVersionId(), event.processingCompletedAt())); }
        catch (JsonProcessingException exception) { throw new NonRetryableKafkaEventException("Semantic index event payload is malformed", exception); }
        catch (SemanticIndexingException exception) { if (!exception.isRetryable()) throw new NonRetryableKafkaEventException("Semantic index event cannot be processed", exception); throw exception; }
    }
}
