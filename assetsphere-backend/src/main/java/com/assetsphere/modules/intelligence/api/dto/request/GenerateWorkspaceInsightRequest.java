package com.assetsphere.modules.intelligence.api.dto.request;

import com.assetsphere.modules.intelligence.api.WorkspaceInsightType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record GenerateWorkspaceInsightRequest(
        @NotNull WorkspaceInsightType type,
        @Size(max = 200) String focus,
        @Size(max = 128) String modelId
) { }
