package com.assetsphere.modules.workspace.api;

import com.assetsphere.modules.common.security.CurrentUser;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.workspace.api.dto.request.AcceptWorkspaceInvitationRequest;
import com.assetsphere.modules.workspace.api.dto.request.ChangeWorkspaceRoleRequest;
import com.assetsphere.modules.workspace.api.dto.request.CreateWorkspaceRequest;
import com.assetsphere.modules.workspace.api.dto.request.InviteWorkspaceMemberRequest;
import com.assetsphere.modules.workspace.api.dto.request.UpdateWorkspaceRequest;
import com.assetsphere.modules.workspace.application.WorkspaceInvitationService;
import com.assetsphere.modules.workspace.application.WorkspaceMembershipService;
import com.assetsphere.modules.workspace.application.WorkspaceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;

@RestController
@RequestMapping("/api/v1/workspaces")
@Tag(name = "Workspaces")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
class WorkspaceController {

    private final WorkspaceService workspaceService;
    private final WorkspaceMembershipService membershipService;
    private final WorkspaceInvitationService invitationService;
    private final CurrentUserProvider currentUserProvider;
    private final ClockProvider clock;

    @GetMapping
    ApiResponse<?> list() {
        return ApiResponse.success(workspaceService.findWorkspacesForUser(currentUserProvider.requireCurrentUser().id()), clock);
    }

    @PostMapping
    ResponseEntity<ApiResponse<?>> create(@Valid @RequestBody CreateWorkspaceRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(workspaceService.create(currentUserProvider.requireCurrentUser().id(), request), clock));
    }

    @GetMapping("/{workspaceId}")
    ApiResponse<?> get(@PathVariable UUID workspaceId) {
        return ApiResponse.success(workspaceService.get(currentUserProvider.requireCurrentUser().id(), workspaceId), clock);
    }

    @PatchMapping("/{workspaceId}")
    ApiResponse<?> update(@PathVariable UUID workspaceId, @Valid @RequestBody UpdateWorkspaceRequest request) {
        return ApiResponse.success(workspaceService.update(currentUserProvider.requireCurrentUser().id(), workspaceId, request), clock);
    }

    @GetMapping("/{workspaceId}/members")
    ApiResponse<?> members(@PathVariable UUID workspaceId) {
        return ApiResponse.success(membershipService.listMembers(currentUserProvider.requireCurrentUser().id(), workspaceId), clock);
    }

    @PostMapping("/{workspaceId}/invitations")
    ResponseEntity<ApiResponse<?>> invite(
            @PathVariable UUID workspaceId,
            @Valid @RequestBody InviteWorkspaceMemberRequest request
    ) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(invitationService.invite(currentUser.id(), currentUser.email(), workspaceId, request), clock));
    }

    @GetMapping("/invitations/validate")
    ApiResponse<?> validateInvitation(@RequestParam String token) {
        return ApiResponse.success(invitationService.validate(token), clock);
    }

    @PostMapping("/invitations/accept")
    ApiResponse<?> accept(@Valid @RequestBody AcceptWorkspaceInvitationRequest request) {
        CurrentUser currentUser = currentUserProvider.requireCurrentUser();
        return ApiResponse.success(invitationService.accept(currentUser.id(), currentUser.email(), request), clock);
    }

    @PostMapping("/invitations/decline")
    ApiResponse<Void> decline(@Valid @RequestBody AcceptWorkspaceInvitationRequest request) {
        invitationService.decline(currentUserProvider.requireCurrentUser().email(), request);
        return ApiResponse.success(null, clock);
    }

    @PatchMapping("/{workspaceId}/members/{memberId}/role")
    ApiResponse<?> changeRole(
            @PathVariable UUID workspaceId,
            @PathVariable UUID memberId,
            @Valid @RequestBody ChangeWorkspaceRoleRequest request
    ) {
        return ApiResponse.success(
                membershipService.changeRole(currentUserProvider.requireCurrentUser().id(), workspaceId, memberId, request),
                clock
        );
    }

    @DeleteMapping("/{workspaceId}/members/{memberId}")
    ResponseEntity<Void> remove(@PathVariable UUID workspaceId, @PathVariable UUID memberId) {
        membershipService.remove(currentUserProvider.requireCurrentUser().id(), workspaceId, memberId);
        return ResponseEntity.noContent().build();
    }
}
