package com.assetsphere.modules.billing.api;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.billing")
public class BillingProperties {
    private Limits free = Limits.freeDefaults();
    private Limits pro = Limits.proDefaults();
    private Limits enterprise = Limits.enterpriseDefaults();

    public PlanEntitlements entitlements(Plan plan) {
        Limits limits = switch (plan) { case FREE -> free; case PRO -> pro; case ENTERPRISE -> enterprise; };
        return new PlanEntitlements(limits.maxAssets, limits.maxStorage.toBytes(), limits.maxMembers, limits.monthlyAiInsights,
                limits.monthlyAskRequests, limits.monthlyEvolutionComparisons, limits.monthlySemanticSearches,
                limits.monthlyQuizGenerations, limits.ocrEnabled, limits.videoTranscriptionEnabled, limits.fullAuditEnabled);
    }

    @Getter
    @Setter
    public static class Limits {
        private int maxAssets;
        private DataSize maxStorage;
        private int maxMembers;
        private long monthlyAiInsights;
        private long monthlyAskRequests;
        private long monthlyEvolutionComparisons;
        private long monthlySemanticSearches;
        private long monthlyQuizGenerations;
        private boolean ocrEnabled;
        private boolean videoTranscriptionEnabled;
        private boolean fullAuditEnabled;

        static Limits freeDefaults() {
            return limits(25, DataSize.ofMegabytes(250), 5, 10, 50, 5, 500, 3, false, false, false);
        }

        static Limits proDefaults() {
            return limits(1_000, DataSize.ofGigabytes(20), 50, 500, 2_000, 250, 20_000, 200, true, true, true);
        }

        static Limits enterpriseDefaults() {
            return limits(10_000, DataSize.ofGigabytes(200), 500, 5_000, 20_000, 2_500, 200_000, 2_000, true, true, true);
        }

        private static Limits limits(int assets, DataSize storage, int members, long ai, long ask, long evolution,
                                     long search, long quiz, boolean ocr, boolean video, boolean audit) {
            Limits limits = new Limits();
            limits.maxAssets = assets;
            limits.maxStorage = storage;
            limits.maxMembers = members;
            limits.monthlyAiInsights = ai;
            limits.monthlyAskRequests = ask;
            limits.monthlyEvolutionComparisons = evolution;
            limits.monthlySemanticSearches = search;
            limits.monthlyQuizGenerations = quiz;
            limits.ocrEnabled = ocr;
            limits.videoTranscriptionEnabled = video;
            limits.fullAuditEnabled = audit;
            return limits;
        }
    }
}
