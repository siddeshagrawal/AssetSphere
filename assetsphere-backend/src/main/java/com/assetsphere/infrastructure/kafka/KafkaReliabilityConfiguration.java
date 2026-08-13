package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.processing.api.ProcessingProperties;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.util.backoff.BackOff;
import org.springframework.kafka.config.TopicBuilder;
import org.apache.kafka.clients.admin.NewTopic;
import io.micrometer.core.instrument.MeterRegistry;

@Configuration
@RequiredArgsConstructor
@Slf4j
class KafkaReliabilityConfiguration {

    private final ProcessingProperties properties;
    private final IntelligenceProperties intelligenceProperties;
    private final SemanticIndexProperties semanticIndexProperties;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Bean
    NewTopic assetUploadedTopic() {
        return TopicBuilder.name(properties.getTopics().getAssetUploaded()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic assetUploadedDeadLetterTopic() {
        return TopicBuilder.name(properties.getTopics().getAssetUploadedDlt()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic assetReadyForIntelligenceTopic() {
        return TopicBuilder.name(intelligenceProperties.getTopics().getAssetReady()).partitions(1).replicas(1).build();
    }

    @Bean
    NewTopic assetReadyForIntelligenceDeadLetterTopic() {
        return TopicBuilder.name(intelligenceProperties.getTopics().getAssetReadyDlt()).partitions(1).replicas(1).build();
    }
    @Bean NewTopic assetReadyForSemanticIndexTopic() { return TopicBuilder.name(semanticIndexProperties.getTopics().getAssetReady()).partitions(1).replicas(1).build(); }
    @Bean NewTopic assetReadyForSemanticIndexDeadLetterTopic() { return TopicBuilder.name(semanticIndexProperties.getTopics().getAssetReadyDlt()).partitions(1).replicas(1).build(); }

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    logAssetUploadDeadLetter(record, exception);
                    recordDltPublished(properties.getTopics().getAssetUploaded());
                    return new TopicPartition(properties.getTopics().getAssetUploadedDlt(), record.partition());
                }
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, retryBackoff());
        errorHandler.addNotRetryableExceptions(NonRetryableKafkaEventException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> recordRetry(record.topic(), deliveryAttempt));
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("kafkaErrorHandler") DefaultErrorHandler kafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    @Bean
    DefaultErrorHandler intelligenceKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, exception) -> {
                    logIntelligenceDeadLetter(record, exception);
                    recordDltPublished(intelligenceProperties.getTopics().getAssetReady());
                    return new TopicPartition(intelligenceProperties.getTopics().getAssetReadyDlt(), record.partition());
                }
        );
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, intelligenceRetryBackoff());
        errorHandler.addNotRetryableExceptions(NonRetryableKafkaEventException.class);
        errorHandler.setRetryListeners((record, exception, deliveryAttempt) -> recordRetry(record.topic(), deliveryAttempt));
        return errorHandler;
    }

    @Bean
    ConcurrentKafkaListenerContainerFactory<String, String> intelligenceKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("intelligenceKafkaErrorHandler") DefaultErrorHandler intelligenceKafkaErrorHandler
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.setCommonErrorHandler(intelligenceKafkaErrorHandler);
        return factory;
    }
    @Bean DefaultErrorHandler semanticKafkaErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, exception) -> { logSemanticDeadLetter(record, exception); recordDltPublished(semanticIndexProperties.getTopics().getAssetReady()); return new TopicPartition(semanticIndexProperties.getTopics().getAssetReadyDlt(), record.partition()); });
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, semanticRetryBackoff()); handler.addNotRetryableExceptions(NonRetryableKafkaEventException.class); handler.setRetryListeners((record, exception, deliveryAttempt) -> recordRetry(record.topic(), deliveryAttempt)); return handler;
    }
    @Bean ConcurrentKafkaListenerContainerFactory<String, String> semanticKafkaListenerContainerFactory(ConsumerFactory<String, String> consumerFactory, @Qualifier("semanticKafkaErrorHandler") DefaultErrorHandler handler) { ConcurrentKafkaListenerContainerFactory<String,String> factory = new ConcurrentKafkaListenerContainerFactory<>(); factory.setConsumerFactory(consumerFactory); factory.setCommonErrorHandler(handler); return factory; }

    private BackOff retryBackoff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(
                Math.max(0, properties.getConsumer().getMaxAttempts() - 1)
        );
        backOff.setInitialInterval(properties.getConsumer().getInitialBackoff().toMillis());
        backOff.setMaxInterval(properties.getConsumer().getMaxBackoff().toMillis());
        backOff.setMultiplier(2.0d);
        return backOff;
    }

    private BackOff intelligenceRetryBackoff() {
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(
                Math.max(0, intelligenceProperties.getConsumer().getMaxAttempts() - 1)
        );
        backOff.setInitialInterval(intelligenceProperties.getConsumer().getInitialBackoff().toMillis());
        backOff.setMaxInterval(intelligenceProperties.getConsumer().getMaxBackoff().toMillis());
        backOff.setMultiplier(2.0d);
        return backOff;
    }
    private BackOff semanticRetryBackoff() { ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(Math.max(0, semanticIndexProperties.getConsumer().getMaxAttempts()-1)); backOff.setInitialInterval(semanticIndexProperties.getConsumer().getInitialBackoff().toMillis()); backOff.setMaxInterval(semanticIndexProperties.getConsumer().getMaxBackoff().toMillis()); backOff.setMultiplier(2.0d); return backOff; }

    private void logAssetUploadDeadLetter(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            AssetUploadedEvent event = objectMapper.readValue(record.value().toString(), AssetUploadedEvent.class);
            log.error("Asset uploaded event exhausted retries eventId={} workspaceId={} assetId={} assetVersionId={}",
                    event.eventId(), event.workspaceId(), event.assetId(), event.assetVersionId(), exception);
        } catch (JsonProcessingException | RuntimeException parsingException) {
            log.error("Asset uploaded event exhausted retries assetId={} topic={} partition={}",
                    record.key(), record.topic(), record.partition(), exception);
        }
    }

    private void logIntelligenceDeadLetter(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            AssetReadyForIntelligenceEvent event = objectMapper.readValue(
                    record.value().toString(), AssetReadyForIntelligenceEvent.class);
            log.error("Intelligence event exhausted retries eventId={} workspaceId={} assetId={} assetVersionId={}",
                    event.eventId(), event.workspaceId(), event.assetId(), event.assetVersionId(), exception);
        } catch (JsonProcessingException | RuntimeException parsingException) {
            log.error("Intelligence event exhausted retries assetId={} topic={} partition={}",
                    record.key(), record.topic(), record.partition(), exception);
        }
    }

    private void logSemanticDeadLetter(ConsumerRecord<?, ?> record, Exception exception) {
        try {
            var event = objectMapper.readValue(record.value().toString(), com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent.class);
            log.error("Semantic indexing event exhausted retries eventId={} workspaceId={} assetId={} assetVersionId={}",
                    event.eventId(), event.workspaceId(), event.assetId(), event.assetVersionId(), exception);
        } catch (JsonProcessingException | RuntimeException parsingException) {
            log.error("Semantic indexing event exhausted retries topic={} partition={}", record.topic(), record.partition(), exception);
        }
    }

    private void recordRetry(String topic, int deliveryAttempt) {
        if (deliveryAttempt > 1) meterRegistry.counter("assetsphere.kafka.retry", "topic", topic).increment();
    }

    private void recordDltPublished(String originalTopic) {
        meterRegistry.counter("assetsphere.kafka.dlt.published", "topic", originalTopic).increment();
    }
}
