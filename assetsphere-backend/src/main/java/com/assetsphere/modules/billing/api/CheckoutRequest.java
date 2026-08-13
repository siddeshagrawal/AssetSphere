package com.assetsphere.modules.billing.api;

import java.util.UUID;

public record CheckoutRequest(UUID workspaceId, UUID userId, Plan plan, long amountMinor,
                              String currency, String receipt) { }
