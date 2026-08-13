package com.assetsphere.modules.billing.api;

public record PlanEntitlements(int maxAssets, long maxStorageBytes, int maxMembers, long monthlyAiInsights,
                               long monthlyAskRequests, long monthlyEvolutionComparisons,
                               long monthlySemanticSearches, long monthlyQuizGenerations,
                               boolean ocrEnabled, boolean videoTranscriptionEnabled, boolean fullAuditEnabled) {
    public long limit(UsageMetric metric) {
        return switch (metric) {
            case AI_INSIGHT -> monthlyAiInsights;
            case ASK -> monthlyAskRequests;
            case EVOLUTION -> monthlyEvolutionComparisons;
            case QUIZ_GENERATION -> monthlyQuizGenerations;
        };
    }
}
