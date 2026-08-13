package com.assetsphere.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingFacade;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;

class DltOperationsServiceTests {
    @Test
    void inspectionMapsBoundedMetadataWithoutExposingPayload() {
        String topic = "assets.uploaded.v1.DLT";
        UUID workspaceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        TopicPartition partition = new TopicPartition(topic, 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 4, "asset", payload(eventId, workspaceId));
        ConsumerFactory<String, String> factory = factoryWith(consumer(topic, partition, record));

        List<DltRecordView> records = service(factory, mock(KafkaTemplate.class)).inspect(workspaceId, 10);

        assertThat(records).singleElement().satisfies(view -> {
            assertThat(view.eventId()).isEqualTo(eventId);
            assertThat(view.workspaceId()).isEqualTo(workspaceId);
            assertThat(view.originalTopic()).isEqualTo("assets.uploaded.v1");
        });
    }

    @Test
    void replayPublishesUnchangedPayloadToOriginalTopic() {
        String topic = "assets.uploaded.v1.DLT";
        UUID workspaceId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        String payload = payload(eventId, workspaceId);
        TopicPartition partition = new TopicPartition(topic, 0);
        ConsumerRecord<String, String> record = new ConsumerRecord<>(topic, 0, 4, "asset", payload);
        ConsumerFactory<String, String> factory = factoryWith(consumer(topic, partition, record));
        KafkaTemplate<String, String> template = mock(KafkaTemplate.class);
        AssetProcessingFacade assetProcessing = mock(AssetProcessingFacade.class);
        when(template.send("assets.uploaded.v1", "asset", payload)).thenReturn(CompletableFuture.completedFuture(null));

        DltReplayResponse response = service(factory, template, assetProcessing,
                mock(SemanticIndexingFacade.class)).replay(workspaceId, UUID.randomUUID(), topic, 0, 4);

        assertThat(response.eventId()).isEqualTo(eventId);
        assertThat(response.status()).isEqualTo("REPUBLISHED");
        verify(assetProcessing).prepareFailedAssetForRetry(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        verify(template).send("assets.uploaded.v1", "asset", payload);
    }

    private DltOperationsService service(ConsumerFactory<String, String> factory, KafkaTemplate<String, String> template) {
        return service(factory, template, mock(AssetProcessingFacade.class), mock(SemanticIndexingFacade.class));
    }

    private DltOperationsService service(ConsumerFactory<String, String> factory, KafkaTemplate<String, String> template,
                                         AssetProcessingFacade assetProcessing,
                                         SemanticIndexingFacade semanticIndexing) {
        return new DltOperationsService(factory, template, new ObjectMapper(), new SimpleMeterRegistry(),
                assetProcessing, semanticIndexing,
                new ProcessingProperties(), new IntelligenceProperties(), new SemanticIndexProperties(), 100);
    }

    @SuppressWarnings("unchecked")
    private ConsumerFactory<String, String> factoryWith(Consumer<String, String> consumer) {
        ConsumerFactory<String, String> factory = mock(ConsumerFactory.class);
        when(factory.createConsumer(anyString(), anyString(), isNull())).thenReturn(consumer);
        return factory;
    }

    @SuppressWarnings("unchecked")
    private Consumer<String, String> consumer(String topic, TopicPartition partition, ConsumerRecord<String, String> record) {
        Consumer<String, String> consumer = mock(Consumer.class);
        when(consumer.partitionsFor(anyString(), org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenAnswer(invocation -> topic.equals(invocation.getArgument(0))
                        ? List.of(new PartitionInfo(topic, 0, null, null, null))
                        : List.of());
        when(consumer.beginningOffsets(List.of(partition))).thenReturn(Map.of(partition, 0L));
        when(consumer.endOffsets(List.of(partition))).thenReturn(Map.of(partition, 5L));
        when(consumer.poll(org.mockito.ArgumentMatchers.any(Duration.class)))
                .thenReturn(new ConsumerRecords<>(Map.of(partition, List.of(record))));
        return consumer;
    }

    private String payload(UUID eventId, UUID workspaceId) {
        return "{\"eventId\":\"" + eventId + "\",\"workspaceId\":\"" + workspaceId
                + "\",\"assetId\":\"" + UUID.randomUUID() + "\",\"assetVersionId\":\"" + UUID.randomUUID() + "\"}";
    }
}
