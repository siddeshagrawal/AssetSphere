package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.api.SemanticIndexingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingFacade;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(prefix = "assetsphere.ops.dlt", name = "enabled", havingValue = "true")
class DltOperationsService {
    private static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration POLL_TIMEOUT = Duration.ofSeconds(1);

    private final ConsumerFactory<String, String> consumerFactory;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final AssetProcessingFacade assetProcessing;
    private final SemanticIndexingFacade semanticIndexing;
    private final int maxInspectionRecords;
    private final Map<String, String> originalTopics;
    private final String assetUploadedTopic;
    private final String semanticReadyTopic;

    DltOperationsService(
            ConsumerFactory<String, String> consumerFactory,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            MeterRegistry meterRegistry,
            AssetProcessingFacade assetProcessing,
            SemanticIndexingFacade semanticIndexing,
            ProcessingProperties processing,
            IntelligenceProperties intelligence,
            SemanticIndexProperties semantic,
            @Value("${assetsphere.ops.dlt.max-inspection-records:100}") int maxInspectionRecords
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.assetProcessing = assetProcessing;
        this.semanticIndexing = semanticIndexing;
        this.maxInspectionRecords = Math.max(1, maxInspectionRecords);
        this.assetUploadedTopic = processing.getTopics().getAssetUploaded();
        this.semanticReadyTopic = semantic.getTopics().getAssetReady();
        this.originalTopics = Map.of(
                processing.getTopics().getAssetUploadedDlt(), processing.getTopics().getAssetUploaded(),
                intelligence.getTopics().getAssetReadyDlt(), intelligence.getTopics().getAssetReady(),
                semantic.getTopics().getAssetReadyDlt(), semantic.getTopics().getAssetReady()
        );
    }

    List<DltRecordView> inspect(UUID workspaceId, int requestedLimit) {
        int limit = Math.min(Math.max(1, requestedLimit), maxInspectionRecords);
        List<DltRecordView> records = new ArrayList<>();
        for (String topic : originalTopics.keySet()) records.addAll(readTail(topic, limit, workspaceId));
        return records.stream().sorted(Comparator.comparing(DltRecordView::failedAt).reversed()).limit(limit).toList();
    }

    DltReplayResponse replay(UUID workspaceId, UUID actorUserId, String topic, int partition, long offset) {
        String configuredOriginalTopic = originalTopics.get(topic);
        if (configuredOriginalTopic == null) throw new InvalidRequestException("Unknown AssetSphere dead-letter topic");
        ConsumerRecord<String, String> record = readOne(topic, partition, offset);
        EventIdentifiers identifiers = identifiers(record);
        if (!workspaceId.equals(identifiers.workspaceId())) throw new ResourceNotFoundException("Dead-letter event was not found");
        String originalTopic = headerText(record, "kafka_dlt-original-topic");
        if (originalTopic == null) originalTopic = configuredOriginalTopic;
        if (!configuredOriginalTopic.equals(originalTopic)) throw new InvalidRequestException("Dead-letter event original topic is invalid");
        prepareRetry(originalTopic, identifiers);
        try {
            kafkaTemplate.send(originalTopic, record.key(), record.value()).get(10, TimeUnit.SECONDS);
            meterRegistry.counter("assetsphere.kafka.dlt.replayed", "topic", originalTopic, "result", "success").increment();
            log.info("DLT event replayed actorUserId={} workspaceId={} eventId={} sourceTopic={} partition={} offset={} originalTopic={}",
                    actorUserId, workspaceId, identifiers.eventId(), topic, partition, offset, originalTopic);
            return new DltReplayResponse(topic, partition, offset, originalTopic, identifiers.eventId(), "REPUBLISHED");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return replayFailed(originalTopic, exception);
        } catch (Exception exception) {
            return replayFailed(originalTopic, exception);
        }
    }

    Set<String> topics() {
        return originalTopics.keySet();
    }

    private void prepareRetry(String originalTopic, EventIdentifiers identifiers) {
        if (originalTopic.equals(assetUploadedTopic)) {
            requireAssetIdentifiers(identifiers);
            assetProcessing.prepareFailedAssetForRetry(identifiers.assetId(), identifiers.assetVersionId());
        } else if (originalTopic.equals(semanticReadyTopic)) {
            requireAssetIdentifiers(identifiers);
            semanticIndexing.prepareFailedIndexForRetry(new SemanticIndexRequest(
                    identifiers.workspaceId(), identifiers.assetId(), identifiers.assetVersionId(), Instant.EPOCH));
        }
    }

    private void requireAssetIdentifiers(EventIdentifiers identifiers) {
        if (identifiers.eventId() == null || identifiers.assetId() == null || identifiers.assetVersionId() == null) {
            throw new InvalidRequestException("Dead-letter event is malformed and cannot be replayed safely");
        }
    }

    private DltReplayResponse replayFailed(String originalTopic, Exception exception) {
        meterRegistry.counter("assetsphere.kafka.dlt.replayed", "topic", originalTopic, "result", "failure").increment();
        throw new ServiceUnavailableException("Dead-letter event could not be replayed", exception);
    }

    private List<DltRecordView> readTail(String topic, int limit, UUID workspaceId) {
        try (var consumer = consumerFactory.createConsumer("assetsphere-dlt-ops", UUID.randomUUID() + "-", null)) {
            var partitions = consumer.partitionsFor(topic, LOOKUP_TIMEOUT).stream()
                    .map(info -> new TopicPartition(topic, info.partition())).toList();
            if (partitions.isEmpty()) return List.of();
            consumer.assign(partitions);
            Map<TopicPartition, Long> beginnings = consumer.beginningOffsets(partitions);
            Map<TopicPartition, Long> ends = consumer.endOffsets(partitions);
            for (TopicPartition partition : partitions) {
                consumer.seek(partition, Math.max(beginnings.get(partition), ends.get(partition) - limit));
            }
            List<DltRecordView> result = new ArrayList<>();
            consumer.poll(POLL_TIMEOUT).forEach(record -> {
                EventIdentifiers ids = identifiers(record);
                if (workspaceId.equals(ids.workspaceId())) result.add(toView(record, ids));
            });
            return result;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Dead-letter records are temporarily unavailable", exception);
        }
    }

    private ConsumerRecord<String, String> readOne(String topic, int partition, long offset) {
        if (partition < 0 || offset < 0) throw new InvalidRequestException("Invalid dead-letter record location");
        TopicPartition topicPartition = new TopicPartition(topic, partition);
        try (var consumer = consumerFactory.createConsumer("assetsphere-dlt-ops", UUID.randomUUID() + "-", null)) {
            consumer.assign(List.of(topicPartition));
            consumer.seek(topicPartition, offset);
            return consumer.poll(POLL_TIMEOUT).records(topicPartition).stream()
                    .filter(record -> record.offset() == offset).findFirst()
                    .orElseThrow(() -> new ResourceNotFoundException("Dead-letter event was not found"));
        } catch (ResourceNotFoundException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new ServiceUnavailableException("Dead-letter record is temporarily unavailable", exception);
        }
    }

    private DltRecordView toView(ConsumerRecord<String, String> record, EventIdentifiers ids) {
        String originalTopic = headerText(record, "kafka_dlt-original-topic");
        return new DltRecordView(record.topic(), record.partition(), record.offset(),
                originalTopic == null ? originalTopics.get(record.topic()) : originalTopic,
                ids.eventId(), ids.workspaceId(), ids.assetId(), ids.assetVersionId(),
                bounded(headerText(record, "kafka_dlt-exception-message"), 300),
                deliveryAttempt(record), Instant.ofEpochMilli(record.timestamp()));
    }

    private EventIdentifiers identifiers(ConsumerRecord<String, String> record) {
        try {
            JsonNode root = objectMapper.readTree(record.value());
            return new EventIdentifiers(uuid(root, "eventId"), uuid(root, "workspaceId"), uuid(root, "assetId"), uuid(root, "assetVersionId"));
        } catch (Exception exception) {
            return new EventIdentifiers(null, null, null, null);
        }
    }

    private UUID uuid(JsonNode root, String field) {
        JsonNode value = root.get(field);
        if (value == null || !value.isTextual()) return null;
        try { return UUID.fromString(value.textValue()); } catch (IllegalArgumentException exception) { return null; }
    }

    private String headerText(ConsumerRecord<String, String> record, String name) {
        Header header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private Integer deliveryAttempt(ConsumerRecord<String, String> record) {
        Header header = record.headers().lastHeader("kafka_deliveryAttempt");
        if (header == null || header.value().length != Integer.BYTES) return null;
        return ByteBuffer.wrap(header.value()).getInt();
    }

    private String bounded(String value, int limit) {
        return value == null ? null : value.substring(0, Math.min(value.length(), limit));
    }

    private record EventIdentifiers(UUID eventId, UUID workspaceId, UUID assetId, UUID assetVersionId) {}
}
