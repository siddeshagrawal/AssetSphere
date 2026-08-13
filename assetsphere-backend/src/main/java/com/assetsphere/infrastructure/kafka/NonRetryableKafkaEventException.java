package com.assetsphere.infrastructure.kafka;

class NonRetryableKafkaEventException extends RuntimeException {

    NonRetryableKafkaEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
