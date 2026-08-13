package com.assetsphere.modules.processing.api;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.processing")
public class ProcessingProperties {

    private org.springframework.util.unit.DataSize maxProcessingFileSize = org.springframework.util.unit.DataSize.ofMegabytes(25);
    private int maxRetainedTextCharacters = 1_000_000;

    private Duration outboxPollInterval = Duration.ofSeconds(1);
    private int outboxBatchSize = 50;
    private Duration outboxLeaseDuration = Duration.ofSeconds(30);
    private int outboxMaxRetries = 8;
    private Duration outboxInitialRetryDelay = Duration.ofSeconds(1);
    private Duration outboxMaxRetryDelay = Duration.ofMinutes(5);
    private Duration kafkaSendTimeout = Duration.ofSeconds(10);
    private Topics topics = new Topics();
    private Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Topics {
        private String assetUploaded = "assets.uploaded.v1";
        private String assetUploadedDlt = "assets.uploaded.v1.DLT";
    }

    @Getter
    @Setter
    public static class Consumer {
        private String groupId = "assetsphere-processing";
        private int maxAttempts = 4;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(15);
        private Duration processingLockLease = Duration.ofSeconds(30);
    }
}
