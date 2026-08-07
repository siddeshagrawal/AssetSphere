package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.Workspace;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class WorkspaceServiceTests {

    private final WorkspaceRepository workspaces = mock(WorkspaceRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final WorkspaceAuthorization authorization = mock(WorkspaceAuthorization.class);
    private final AuditService audit = mock(AuditService.class);
    private final WorkspaceService service = new WorkspaceService(workspaces, members, authorization, () -> Instant.parse("2026-08-07T00:00:00Z"), audit);

    @Test
    void personalWorkspaceCreationGivesCreatorOwnerMembership() {
        UUID userId = UUID.randomUUID();
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(UUID.randomUUID());
        when(workspace.getName()).thenReturn("Ada Workspace");
        when(workspace.getSlug()).thenReturn("personal-" + userId);
        when(workspaces.save(any(Workspace.class))).thenReturn(workspace);

        service.createPersonalWorkspace(userId, "Ada");

        ArgumentCaptor<WorkspaceMember> membership = ArgumentCaptor.forClass(WorkspaceMember.class);
        verify(members).save(membership.capture());
        assertThat(membership.getValue().getUserId()).isEqualTo(userId);
        assertThat(membership.getValue().getRole()).isEqualTo(WorkspaceRole.OWNER);
        assertThat(membership.getValue().getStatus()).isEqualTo(MembershipStatus.ACTIVE);
    }

    @Test
    void listsWorkspacesWithOneBatchLookupInsteadOfOneLookupPerMembership() {
        UUID userId = UUID.randomUUID();
        UUID firstWorkspaceId = UUID.randomUUID();
        UUID secondWorkspaceId = UUID.randomUUID();
        WorkspaceMember firstMembership = new WorkspaceMember(firstWorkspaceId, userId, WorkspaceRole.OWNER, userId, Instant.now());
        WorkspaceMember secondMembership = new WorkspaceMember(secondWorkspaceId, userId, WorkspaceRole.MEMBER, userId, Instant.now());
        Workspace firstWorkspace = workspace(firstWorkspaceId, "First", "first");
        Workspace secondWorkspace = workspace(secondWorkspaceId, "Second", "second");

        when(members.findByUserIdAndStatusOrderByCreatedAtAsc(userId, MembershipStatus.ACTIVE))
                .thenReturn(List.of(firstMembership, secondMembership));
        when(workspaces.findAllById(List.of(firstWorkspaceId, secondWorkspaceId)))
                .thenReturn(List.of(firstWorkspace, secondWorkspace));

        assertThat(service.findWorkspacesForUser(userId)).hasSize(2);

        verify(workspaces).findAllById(List.of(firstWorkspaceId, secondWorkspaceId));
        verify(workspaces, never()).findById(any());
    }

    private Workspace workspace(UUID id, String name, String slug) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(id);
        when(workspace.getName()).thenReturn(name);
        when(workspace.getSlug()).thenReturn(slug);
        return workspace;
    }
}
