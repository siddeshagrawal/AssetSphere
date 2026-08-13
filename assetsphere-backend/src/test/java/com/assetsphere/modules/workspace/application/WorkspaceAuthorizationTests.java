package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class WorkspaceAuthorizationTests {

    private final WorkspaceMemberRepository members = Mockito.mock(WorkspaceMemberRepository.class);
    private final WorkspaceAuthorization authorization = new WorkspaceAuthorization(members);

    @Test
    void allowsAnActiveMemberToAccessWorkspace() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.MEMBER)));

        assertThatCode(() -> authorization.requireActiveMembership(workspaceId, userId)).doesNotThrowAnyException();
    }

    @Test
    void hidesWorkspaceFromNonMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authorization.requireActiveMembership(workspaceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void hidesWorkspaceFromRemovedMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        WorkspaceMember removedMember = member(workspaceId, userId, WorkspaceRole.MEMBER);
        removedMember.remove();
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(removedMember));

        assertThatThrownBy(() -> authorization.requireActiveMembership(workspaceId, userId))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsMemberForOwnerOnlyAction() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(member(workspaceId, userId, WorkspaceRole.MEMBER)));

        assertThatThrownBy(() -> authorization.requireAnyRole(workspaceId, userId, WorkspaceRole.OWNER))
                .isInstanceOf(AuthorizationDeniedException.class);
    }

    private WorkspaceMember member(UUID workspaceId, UUID userId, WorkspaceRole role) {
        return new WorkspaceMember(workspaceId, userId, role, UUID.randomUUID(), Instant.now());
    }
}
