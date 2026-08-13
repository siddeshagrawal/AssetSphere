package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record WorkspaceQuestionAnsweringRequest(
        String question,
        String modelId,
        List<WorkspaceQuestionAnsweringSource> sources
) {
    public WorkspaceQuestionAnsweringRequest {
        sources = List.copyOf(sources);
    }
}
