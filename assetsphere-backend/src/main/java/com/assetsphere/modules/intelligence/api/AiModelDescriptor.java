package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.billing.api.Plan;
import java.util.Set;

public record AiModelDescriptor(String provider, String modelId, String displayName,
                                Set<String> capabilities, Plan minimumPlan, boolean enabled) { }
