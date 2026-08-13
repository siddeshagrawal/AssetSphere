package com.assetsphere.modules.workspace.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.security.UserIdentityDirectory;
import com.assetsphere.modules.workspace.api.dto.request.ChangeWorkspaceRoleRequest;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceMemberResponse;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceMembershipService {

    private final WorkspaceMemberRepository members;
    private final WorkspaceAuthorization authorization;
    private final AuditService audit;
    private final UserIdentityDirectory userIdentities;

    @Transactional(readOnly = true)
    public List<WorkspaceMemberResponse> listMembers(UUID actorUserId, UUID workspaceId) {
        authorization.requireActiveMembership(workspaceId, actorUserId);
        List<WorkspaceMember> activeMembers = members.findByWorkspaceIdAndStatus(workspaceId, MembershipStatus.ACTIVE);
        var identities = userIdentities.findByIds(activeMembers.stream().map(WorkspaceMember::getUserId).toList());
        return activeMembers.stream()
                .map(member -> toResponse(member, identities.get(member.getUserId())))
                .toList();
    }

    @Transactional
    public WorkspaceMemberResponse changeRole(
            UUID actorUserId,
            UUID workspaceId,
            UUID memberId,
            ChangeWorkspaceRoleRequest request
    ) {
        authorization.requireAnyRole(workspaceId, actorUserId, WorkspaceRole.OWNER);
        WorkspaceMember member = requireMember(workspaceId, memberId);
        WorkspaceRole requestedRole = WorkspaceRole.valueOf(request.role().name());

        if (member.getRole() == WorkspaceRole.OWNER
                && requestedRole != WorkspaceRole.OWNER
                && activeOwnerCount(workspaceId) <= 1) {
            throw new BusinessRuleViolationException("A workspace must retain an active owner");
        }

        member.changeRole(requestedRole);
        audit.record(actorUserId, AuditAction.WORKSPACE_MEMBER_ROLE_CHANGED, workspaceId, "WORKSPACE_MEMBER", memberId, "{}");
        return toResponse(member, userIdentities.findByIds(List.of(member.getUserId())).get(member.getUserId()));
    }

    @Transactional
    public void remove(UUID actorUserId, UUID workspaceId, UUID memberId) {
        authorization.requireAnyRole(workspaceId, actorUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        WorkspaceMember target = requireMember(workspaceId, memberId);
        WorkspaceMember requester = members.findByWorkspaceIdAndUserId(workspaceId, actorUserId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member not found"));

        if (target.getUserId().equals(actorUserId)) {
            throw new BusinessRuleViolationException("Members cannot remove themselves from a workspace");
        }

        if (requester.getRole() == WorkspaceRole.ADMIN && !canAdminRemove(target.getRole())) {
            throw new AuthorizationDeniedException("An administrator cannot remove an owner or another administrator");
        }
        if (target.getRole() == WorkspaceRole.OWNER && activeOwnerCount(workspaceId) <= 1) {
            throw new BusinessRuleViolationException("A workspace must retain an active owner");
        }

        target.remove();
        audit.record(actorUserId, AuditAction.WORKSPACE_MEMBER_REMOVED, workspaceId, "WORKSPACE_MEMBER", memberId, "{}");
    }

    private WorkspaceMember requireMember(UUID workspaceId, UUID memberId) {
        WorkspaceMember member = members.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace member not found"));
        if (!member.getWorkspaceId().equals(workspaceId)) {
            throw new ResourceNotFoundException("Workspace member not found");
        }
        return member;
    }

    private long activeOwnerCount(UUID workspaceId) {
        return members.countByWorkspaceIdAndStatusAndRole(workspaceId, MembershipStatus.ACTIVE, WorkspaceRole.OWNER);
    }

    private boolean canAdminRemove(WorkspaceRole role) {
        return role == WorkspaceRole.MEMBER || role == WorkspaceRole.VIEWER || role == WorkspaceRole.AUDITOR;
    }

    private WorkspaceMemberResponse toResponse(
            WorkspaceMember member,
            UserIdentityDirectory.UserIdentity identity
    ) {
        return new WorkspaceMemberResponse(
                member.getId(),
                member.getUserId(),
                identity == null ? null : identity.displayName(),
                identity == null ? null : identity.email(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }
}
