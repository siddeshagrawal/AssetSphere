package com.assetsphere.infrastructure.kafka;

public interface DomainEventPublisher {
    void publish(String topic, Object event);
}
