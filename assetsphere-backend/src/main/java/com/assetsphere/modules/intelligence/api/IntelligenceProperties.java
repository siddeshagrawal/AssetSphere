package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import com.assetsphere.modules.billing.api.Plan;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.ai")
public class IntelligenceProperties {

    private boolean enabled;
    private IntelligenceProvider provider = IntelligenceProvider.OPENAI;
    private String model = "gpt-4o-mini";
    private List<Model> models = new ArrayList<>();
    private int maxInputCharacters = 24_000;
    private double temperature = 0.1d;
    private int maxTags = 8;
    private int maxKeyPoints = 6;
    private int maxSummaryCharacters = 2_000;
    private int maxTagCharacters = 80;
    private int maxKeyPointCharacters = 500;
    private int maxRagSources = 8;
    private int maxRagContextCharacters = 16_000;
    private int maxRagSourceCharacters = 2_500;
    private Duration processingLockLease = Duration.ofMinutes(2);
    private Topics topics = new Topics();
    private Consumer consumer = new Consumer();

    @Getter
    @Setter
    public static class Topics {
        private String assetReady = AssetReadyForIntelligenceEvent.TOPIC;
        private String assetReadyDlt = AssetReadyForIntelligenceEvent.TOPIC + ".DLT";
    }

    @Getter
    @Setter
    public static class Consumer {
        private String groupId = "assetsphere-intelligence";
        private int maxAttempts = 4;
        private Duration initialBackoff = Duration.ofSeconds(1);
        private Duration maxBackoff = Duration.ofSeconds(15);
    }

    @Getter @Setter
    public static class Model {
        private IntelligenceProvider provider = IntelligenceProvider.OPENAI;
        private String modelId;
        private String displayName;
        private Set<String> capabilities = Set.of("ASK", "INTELLIGENCE", "EVOLUTION", "QUIZ");
        private Plan minimumPlan = Plan.FREE;
        private boolean enabled = true;
        private boolean defaultModel;
    }
}
