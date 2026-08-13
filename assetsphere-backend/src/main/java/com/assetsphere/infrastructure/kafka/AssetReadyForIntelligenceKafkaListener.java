package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.intelligence.api.AssetReadyForIntelligenceFacade;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.IntelligenceProviderException;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class AssetReadyForIntelligenceKafkaListener {

    private final ObjectMapper objectMapper;
    private final AssetReadyForIntelligenceFacade intelligence;
    private final IntelligenceProperties properties;

    @KafkaListener(
            topics = "${assetsphere.ai.topics.asset-ready}",
            groupId = "${assetsphere.ai.consumer.group-id}",
            containerFactory = "intelligenceKafkaListenerContainerFactory"
    )
    void consume(String payload) {
        try {
            intelligence.process(read(payload));
        } catch (IntelligenceProviderException exception) {
            if (!exception.isRetryable()) {
                throw new NonRetryableKafkaEventException("Intelligence event cannot be processed", exception);
            }
            throw exception;
        }
    }

    @KafkaListener(
            topics = "${assetsphere.ai.topics.asset-ready-dlt}",
            groupId = "${assetsphere.ai.consumer.group-id}-dlt",
            containerFactory = "intelligenceKafkaListenerContainerFactory"
    )
    void consumeTerminalFailure(String payload) {
        try {
            intelligence.markFailed(read(payload), "KAFKA_DLT");
        } catch (RuntimeException exception) {
            // A malformed terminal event cannot be associated with an Intelligence row and must not poison the DLT.
            log.error("Intelligence DLT event could not be finalized topic={}", properties.getTopics().getAssetReadyDlt());
        }
    }

    private AssetReadyForIntelligenceEvent read(String payload) {
        try {
            return objectMapper.readValue(payload, AssetReadyForIntelligenceEvent.class);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaEventException("Intelligence event payload is malformed", exception);
        }
    }
}
