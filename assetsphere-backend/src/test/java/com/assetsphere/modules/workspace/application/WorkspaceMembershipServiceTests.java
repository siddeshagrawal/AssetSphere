package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.security.UserIdentityDirectory;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import com.assetsphere.modules.workspace.api.dto.request.ChangeWorkspaceRoleRequest;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceMembershipServiceTests {

    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final WorkspaceAuthorization authorization = mock(WorkspaceAuthorization.class);
    private final WorkspaceMembershipService service = new WorkspaceMembershipService(
            members, authorization, mock(AuditService.class), mock(UserIdentityDirectory.class));

    @Test
    void preventsDemotionOfFinalActiveOwner() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WorkspaceMember owner = new WorkspaceMember(workspaceId, UUID.randomUUID(), WorkspaceRole.OWNER, ownerId, Instant.now());
        when(members.findById(ownerId)).thenReturn(Optional.of(owner));
        when(members.countByWorkspaceIdAndStatusAndRole(workspaceId, MembershipStatus.ACTIVE, WorkspaceRole.OWNER)).thenReturn(1L);
        doNothing().when(authorization).requireAnyRole(workspaceId, ownerId, WorkspaceRole.OWNER);

        assertThatThrownBy(() -> service.changeRole(ownerId, workspaceId, ownerId, new ChangeWorkspaceRoleRequest(WorkspaceRoleView.ADMIN)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("A workspace must retain an active owner");
    }

    @Test
    void preventsRemovalOfFinalActiveOwner() {
        UUID workspaceId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        WorkspaceMember owner = new WorkspaceMember(workspaceId, ownerId, WorkspaceRole.OWNER, ownerId, Instant.now());
        when(members.findById(ownerId)).thenReturn(Optional.of(owner));
        when(members.findByWorkspaceIdAndUserId(workspaceId, ownerId)).thenReturn(Optional.of(owner));
        when(members.countByWorkspaceIdAndStatusAndRole(workspaceId, MembershipStatus.ACTIVE, WorkspaceRole.OWNER)).thenReturn(1L);
        doNothing().when(authorization).requireAnyRole(workspaceId, ownerId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> service.remove(ownerId, workspaceId, ownerId))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Members cannot remove themselves from a workspace");
    }

    @Test
    void preventsSelfRemoval() {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WorkspaceMember member = new WorkspaceMember(workspaceId, actorId, WorkspaceRole.ADMIN, UUID.randomUUID(), Instant.now());
        when(members.findById(member.getId())).thenReturn(Optional.of(member));
        when(members.findByWorkspaceIdAndUserId(workspaceId, actorId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.remove(actorId, workspaceId, member.getId()))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Members cannot remove themselves from a workspace");
    }

    @Test
    void rejectsRemovalWithoutManagementPermission() {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        doThrow(new com.assetsphere.modules.common.exception.AuthorizationDeniedException("Insufficient workspace permission"))
                .when(authorization).requireAnyRole(workspaceId, actorId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);

        assertThatThrownBy(() -> service.remove(actorId, workspaceId, memberId))
                .isInstanceOf(com.assetsphere.modules.common.exception.AuthorizationDeniedException.class);
        verify(members, org.mockito.Mockito.never()).findById(any());
    }
}
