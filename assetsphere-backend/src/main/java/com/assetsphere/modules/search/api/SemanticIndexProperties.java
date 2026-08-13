package com.assetsphere.modules.search.api;

import java.time.Duration;
import com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.ai.embedding")
public class SemanticIndexProperties {

    public static final int TEXT_EMBEDDING_3_SMALL_DIMENSION = 1536;

    private boolean enabled;
    private String model = "text-embedding-3-small";
    private int dimension = TEXT_EMBEDDING_3_SMALL_DIMENSION;
    private int batchSize = 16;
    private int chunkSize = 1_000;
    private int chunkOverlap = 160;
    private int maxChunksPerDocument = 100;
    private Duration processingLockLease = Duration.ofMinutes(2);
    private int semanticSearchLimit = 20;
    private double minimumSimilarity = 0.35d;
    private RateLimit semanticSearchRateLimit = new RateLimit(20, Duration.ofMinutes(1));
    private RateLimit ragRateLimit = new RateLimit(10, Duration.ofMinutes(1));
    private Topics topics = new Topics();
    private Consumer consumer = new Consumer();
    @Getter @Setter public static class Topics { private String assetReady = AssetReadyForSemanticIndexEvent.TOPIC; private String assetReadyDlt = AssetReadyForSemanticIndexEvent.TOPIC + ".DLT"; }
    @Getter @Setter public static class Consumer { private String groupId = "assetsphere-semantic-index"; private int maxAttempts = 4; private Duration initialBackoff = Duration.ofSeconds(1); private Duration maxBackoff = Duration.ofSeconds(15); }

    @Getter
    @Setter
    public static class RateLimit {
        private int permits;
        private Duration window;

        public RateLimit() {
        }

        public RateLimit(int permits, Duration window) {
            this.permits = permits;
            this.window = window;
        }
    }

    public void validateSchemaDimension() {
        if (dimension != TEXT_EMBEDDING_3_SMALL_DIMENSION) {
            throw new IllegalStateException("Configured embedding dimension does not match V9 vector(1536) schema");
        }
    }
}
