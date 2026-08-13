package com.assetsphere.modules.billing.api.dto.response;

public record BillingUsageResponse(long assets, long storageBytes, long aiInsights,
                                   long askRequests, long evolutionComparisons, long quizGenerations) { }
