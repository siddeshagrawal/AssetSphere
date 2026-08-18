package com.assetsphere.modules.billing.api;

import java.time.Instant;

public record ProviderSubscriptionState(String externalSubscriptionId,
                                        Instant periodStart,
                                        Instant periodEnd,
                                        boolean cancelAtPeriodEnd,
                                        ProviderSubscriptionStatus status) { }