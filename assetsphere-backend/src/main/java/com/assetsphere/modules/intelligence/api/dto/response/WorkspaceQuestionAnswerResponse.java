package com.assetsphere.modules.intelligence.api.dto.response;

import java.util.List;

public record WorkspaceQuestionAnswerResponse(
        String answer,
        List<WorkspaceAnswerCitationResponse> citations
) {
    public WorkspaceQuestionAnswerResponse {
        citations = List.copyOf(citations);
    }
}
