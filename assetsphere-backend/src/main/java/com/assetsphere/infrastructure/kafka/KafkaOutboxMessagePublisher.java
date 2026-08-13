package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.processing.api.OutboxMessagePublisher;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class KafkaOutboxMessagePublisher implements OutboxMessagePublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ProcessingProperties properties;

    @Override
    public void publish(UUID eventId, UUID aggregateId, String topic, String payload) {
        try {
            kafkaTemplate.send(topic, aggregateId.toString(), payload)
                    .get(properties.getKafkaSendTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            throw new KafkaPublicationException(eventId, exception);
        }
    }
}
