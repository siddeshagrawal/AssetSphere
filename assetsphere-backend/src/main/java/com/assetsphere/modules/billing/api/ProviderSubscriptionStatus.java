package com.assetsphere.modules.billing.api;

public enum ProviderSubscriptionStatus {
    ACTIVE,
    TRIALING,
    PAST_DUE,
    UNPAID,
    CANCELED,
    INCOMPLETE,
    INCOMPLETE_EXPIRED,
    PAUSED,
    UNKNOWN;

    public boolean entitled() {
        return this == ACTIVE || this == TRIALING;
    }

    public boolean terminal() {
        return this == CANCELED || this == INCOMPLETE_EXPIRED;
    }
}
