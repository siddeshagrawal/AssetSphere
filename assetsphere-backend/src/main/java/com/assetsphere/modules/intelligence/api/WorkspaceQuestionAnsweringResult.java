package com.assetsphere.modules.intelligence.api;

import java.util.List;

public record WorkspaceQuestionAnsweringResult(
        String answer,
        List<String> citedSourceIds
) {
    public WorkspaceQuestionAnsweringResult {
        citedSourceIds = citedSourceIds == null ? List.of() : List.copyOf(citedSourceIds);
    }
}
