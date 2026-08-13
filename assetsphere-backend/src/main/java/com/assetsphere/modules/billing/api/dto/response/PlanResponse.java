package com.assetsphere.modules.billing.api.dto.response;

import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.PlanEntitlements;

public record PlanResponse(Plan plan, PlanEntitlements entitlements) { }
