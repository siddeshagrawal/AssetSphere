package com.assetsphere.modules.workspace.api;

import com.assetsphere.modules.audit.api.WorkspaceActivityQuery;
import com.assetsphere.modules.audit.api.WorkspaceActivityResponse;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/activity")
@Tag(name = "Workspace Activity", description = "Bounded audit activity for an authorized workspace")
class WorkspaceActivityController {
    private final WorkspaceActivityQuery activity;
    private final WorkspaceAccessFacade workspaceAccess;
    private final CurrentUserProvider currentUser;
    private final ClockProvider clock;

    @GetMapping
    @Operation(summary = "List recent workspace activity")
    ApiResponse<PageResponse<WorkspaceActivityResponse>> list(@PathVariable UUID workspaceId,
                                                               @RequestParam(defaultValue = "0") int page,
                                                               @RequestParam(defaultValue = "20") int size) {
        workspaceAccess.requireActiveMembership(workspaceId, currentUser.requireCurrentUser().id());
        var result = activity.recent(workspaceId, page, size);
        return ApiResponse.success(new PageResponse<>(result.content(), result.page(), result.size(),
                result.totalElements(), result.totalPages()), clock);
    }
}
