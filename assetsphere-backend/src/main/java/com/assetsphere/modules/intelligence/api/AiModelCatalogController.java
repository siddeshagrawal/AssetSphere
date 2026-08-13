package com.assetsphere.modules.intelligence.api;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.intelligence.application.AiModelCatalogService;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/ai/models")
class AiModelCatalogController {
    private final AiModelCatalogService catalog;
    private final WorkspaceAccessFacade workspaceAccess;
    private final CurrentUserProvider currentUser;
    private final ClockProvider clock;

    @GetMapping
    ApiResponse<List<AiModelDescriptor>> list(@PathVariable UUID workspaceId) {
        workspaceAccess.requireActiveMembership(workspaceId, currentUser.requireCurrentUser().id());
        return ApiResponse.success(catalog.available(workspaceId), clock);
    }
}
