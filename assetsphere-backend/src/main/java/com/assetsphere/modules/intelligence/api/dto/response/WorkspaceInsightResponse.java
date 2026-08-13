package com.assetsphere.modules.intelligence.api.dto.response;

import java.util.List;

public record WorkspaceInsightResponse(
        String type,
        String summary,
        List<Item> items,
        List<WorkspaceAnswerCitationResponse> citations
) {
    public record Item(String title, String secondary, String detail, String severity, List<String> sourceIds) { }
}
