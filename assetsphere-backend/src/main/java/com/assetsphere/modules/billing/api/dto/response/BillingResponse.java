package com.assetsphere.modules.billing.api.dto.response;

import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.PlanEntitlements;
import com.assetsphere.modules.billing.api.PaymentStatus;
import com.assetsphere.modules.billing.api.SubscriptionStatus;
import com.assetsphere.modules.billing.api.PaymentProvider;
import java.time.Instant;

public record BillingResponse(Plan plan, SubscriptionStatus subscriptionStatus, PlanEntitlements entitlements,
                              BillingUsageResponse usage, BillingUsageResponse remaining,
                              Instant periodStart, Instant periodEnd, PaymentStatus latestPaymentStatus,
                              PaymentProvider paymentProvider, boolean autoRenew, boolean cancelAtPeriodEnd) { }
