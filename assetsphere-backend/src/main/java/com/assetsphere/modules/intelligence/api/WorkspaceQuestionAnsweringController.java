package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.intelligence.api.dto.request.WorkspaceQuestionRequest;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceQuestionAnswerResponse;
import com.assetsphere.modules.intelligence.application.WorkspaceRagApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/ask")
@Tag(name = "Intelligence")
@SecurityRequirement(name = "bearerAuth")
class WorkspaceQuestionAnsweringController {

    private final WorkspaceRagApplicationService ragService;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clock;

    @PostMapping
    @Operation(summary = "Answer a question using trusted workspace evidence")
    ApiResponse<WorkspaceQuestionAnswerResponse> ask(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody WorkspaceQuestionRequest request
    ) {
        return ApiResponse.success(ragService.ask(
                currentUserProvider.requireCurrentUser().id(), workspaceId, request.question(), request.modelId()), clock);
    }
}
