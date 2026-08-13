package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record WorkspaceInsightRequest(
        WorkspaceInsightType type,
        String focus,
        String modelId,
        List<Source> sources
) {
    public record Source(String id, String text) { }
}
