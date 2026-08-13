package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateWorkspaceInsightRequest;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceInsightResponse;
import com.assetsphere.modules.intelligence.application.WorkspaceInsightApplicationService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/v1/workspaces/{workspaceId}")
@Tag(name = "Insights")
class WorkspaceInsightController {
    private final WorkspaceInsightApplicationService insights;
    private final CurrentUserProvider currentUser;
    private final ClockProvider clock;

    @PostMapping("/insights")
    @Operation(summary = "Generate a grounded workspace insight")
    ApiResponse<WorkspaceInsightResponse> workspace(
            @PathVariable UUID workspaceId, @Valid @RequestBody GenerateWorkspaceInsightRequest request) {
        return ApiResponse.success(insights.forWorkspace(
                currentUser.requireCurrentUser().id(), workspaceId, request), clock);
    }

    @PostMapping("/assets/{assetId}/versions/{versionNumber}/insights")
    @Operation(summary = "Generate a grounded insight from an exact asset version")
    ApiResponse<WorkspaceInsightResponse> asset(
            @PathVariable UUID workspaceId, @PathVariable UUID assetId, @PathVariable int versionNumber,
            @Valid @RequestBody GenerateWorkspaceInsightRequest request) {
        return ApiResponse.success(insights.forAsset(
                currentUser.requireCurrentUser().id(), workspaceId, assetId, versionNumber, request), clock);
    }
}
