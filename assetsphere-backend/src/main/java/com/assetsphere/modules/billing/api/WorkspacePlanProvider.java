package com.assetsphere.modules.billing.api;

import java.util.UUID;

public interface WorkspacePlanProvider { Plan currentPlan(UUID workspaceId); }
