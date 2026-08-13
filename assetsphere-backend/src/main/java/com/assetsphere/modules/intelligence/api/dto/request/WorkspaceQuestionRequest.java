package com.assetsphere.modules.intelligence.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record WorkspaceQuestionRequest(
        @NotBlank
        @Size(max = 200)
        String question,
        @Size(max = 128) String modelId
) {
}
