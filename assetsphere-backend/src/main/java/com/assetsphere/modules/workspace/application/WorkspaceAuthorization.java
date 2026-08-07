package com.assetsphere.modules.workspace.application;

import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class WorkspaceAuthorization implements WorkspaceAccessFacade {

    private final WorkspaceMemberRepository members;

    public boolean isActiveMember(UUID workspaceId, UUID userId) {
        return members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMember::isActive)
                .orElse(false);
    }

    public boolean hasAnyRole(UUID workspaceId, UUID userId, WorkspaceRole... roles) {
        Set<WorkspaceRole> permittedRoles = Set.copyOf(Arrays.asList(roles));
        return members.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(WorkspaceMember::isActive)
                .map(member -> permittedRoles.contains(member.getRole()))
                .orElse(false);
    }

    @Override
    public void requireActiveMembership(UUID workspaceId, UUID userId) {
        if (!isActiveMember(workspaceId, userId)) {
            throw new ResourceNotFoundException("Workspace not found");
        }
    }

    public void requireAnyRole(UUID workspaceId, UUID userId, WorkspaceRole... roles) {
        if (!hasAnyRole(workspaceId, userId, roles)) {
            throw new AuthorizationDeniedException("Insufficient workspace permission");
        }
    }

    @Override
    public void requireRole(UUID workspaceId, UUID userId, Set<WorkspaceRoleView> roles) {
        WorkspaceRole[] domainRoles = roles.stream()
                .map(role -> WorkspaceRole.valueOf(role.name()))
                .toArray(WorkspaceRole[]::new);
        requireAnyRole(workspaceId, userId, domainRoles);
    }
}
