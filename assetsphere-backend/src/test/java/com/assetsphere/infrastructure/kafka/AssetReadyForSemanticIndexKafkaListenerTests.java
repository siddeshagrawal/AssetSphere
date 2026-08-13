package com.assetsphere.infrastructure.kafka;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.search.api.SemanticIndexingFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.annotation.KafkaListener;

@ExtendWith(MockitoExtension.class)
class AssetReadyForSemanticIndexKafkaListenerTests {
    @Mock SemanticIndexingFacade facade;
    @Test void mapsProcessingEventToSearchRequest() throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var listener = new AssetReadyForSemanticIndexKafkaListener(mapper, facade);
        String payload = mapper.writeValueAsString(new com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH, 1));
        listener.consume(payload);
        verify(facade).process(any());
    }

    @Test void usesConfiguredSemanticConsumerAndRetryContainer() throws Exception {
        KafkaListener listener = AssetReadyForSemanticIndexKafkaListener.class.getDeclaredMethod("consume", String.class).getAnnotation(KafkaListener.class);
        assertThat(listener.containerFactory()).isEqualTo("semanticKafkaListenerContainerFactory");
        assertThat(listener.topics()).containsExactly("${assetsphere.ai.embedding.topics.asset-ready}");
        assertThat(listener.groupId()).isEqualTo("${assetsphere.ai.embedding.consumer.group-id}");
    }
}
