package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
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
    private final WorkspaceMembershipService service = new WorkspaceMembershipService(members, authorization, mock(AuditService.class));

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
                .hasMessage("A workspace must retain an active owner");
    }
}
