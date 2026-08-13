package com.assetsphere.infrastructure.kafka;

import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/ops/dlt")
@ConditionalOnProperty(prefix = "assetsphere.ops.dlt", name = "enabled", havingValue = "true")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Operations")
class DltOperationsController {
    private static final Set<WorkspaceRoleView> OPERATORS = Set.of(WorkspaceRoleView.OWNER, WorkspaceRoleView.ADMIN);
    private final DltOperationsService operations;
    private final WorkspaceAccessFacade workspaceAccess;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clockProvider;

    @GetMapping
    ResponseEntity<ApiResponse<List<DltRecordView>>> inspect(
            @PathVariable UUID workspaceId,
            @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit
    ) {
        UUID userId = currentUserProvider.requireCurrentUser().id();
        workspaceAccess.requireRole(workspaceId, userId, OPERATORS);
        return ResponseEntity.ok(ApiResponse.success(operations.inspect(workspaceId, limit), clockProvider));
    }

    @PostMapping("/{topic}/{partition}/{offset}/replay")
    ResponseEntity<ApiResponse<DltReplayResponse>> replay(
            @PathVariable UUID workspaceId,
            @PathVariable String topic,
            @PathVariable int partition,
            @PathVariable long offset
    ) {
        UUID userId = currentUserProvider.requireCurrentUser().id();
        workspaceAccess.requireRole(workspaceId, userId, OPERATORS);
        return ResponseEntity.ok(ApiResponse.success(operations.replay(workspaceId, userId, topic, partition, offset), clockProvider));
    }
}
