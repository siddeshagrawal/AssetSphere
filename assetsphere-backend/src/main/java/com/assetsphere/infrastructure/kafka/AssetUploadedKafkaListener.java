package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.processing.api.AssetEventProcessingFacade;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
class AssetUploadedKafkaListener {

    private final ObjectMapper objectMapper;
    private final AssetEventProcessingFacade processor;
    private final ProcessingProperties properties;

    @KafkaListener(topics = "${assetsphere.processing.topics.asset-uploaded}", groupId = "${assetsphere.processing.consumer.group-id}")
    void consume(String payload) {
        processor.process(read(payload));
    }

    void consumeDeadLetter(String payload, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        try {
            processor.markFailed(read(payload));
            // Payload is deliberately not logged because asset event fields may include user-provided filenames.
            log.error("Asset uploaded event moved to DLT topic={}", topic);
        } catch (RuntimeException exception) {
            log.error("Malformed asset uploaded event moved to DLT topic={}", topic);
        }
    }

    @KafkaListener(topics = "${assetsphere.processing.topics.asset-uploaded-dlt}", groupId = "${assetsphere.processing.consumer.group-id}-dlt")
    void consumeTerminalFailure(String payload) {
        consumeDeadLetter(payload, properties.getTopics().getAssetUploadedDlt());
    }

    private AssetUploadedEvent read(String payload) {
        try {
            return objectMapper.readValue(payload, AssetUploadedEvent.class);
        } catch (JsonProcessingException exception) {
            throw new NonRetryableKafkaEventException("Asset uploaded event payload is malformed", exception);
        }
    }
}
