package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.dto.request.CreateWorkspaceRequest;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.Workspace;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.domain.WorkspaceStatus;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

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

    @Test
    void allowsDifferentCreatorsToUseTheSameSlug() {
        UUID creatorA = UUID.randomUUID();
        UUID creatorB = UUID.randomUUID();
        Workspace savedA = workspace(UUID.randomUUID(), "Team A", "team");
        Workspace savedB = workspace(UUID.randomUUID(), "Team B", "team");
        when(workspaces.saveAndFlush(any(Workspace.class))).thenReturn(savedA, savedB);

        service.create(creatorA, new CreateWorkspaceRequest("Team A", "Team", null));
        service.create(creatorB, new CreateWorkspaceRequest("Team B", "team", null));

        verify(workspaces).existsByCreatorUserIdAndSlug(creatorA, "team");
        verify(workspaces).existsByCreatorUserIdAndSlug(creatorB, "team");
        ArgumentCaptor<Workspace> created = ArgumentCaptor.forClass(Workspace.class);
        verify(workspaces, org.mockito.Mockito.times(2)).saveAndFlush(created.capture());
        assertThat(created.getAllValues()).extracting(Workspace::getCreatorUserId)
                .containsExactly(creatorA, creatorB);
        assertThat(created.getAllValues()).extracting(Workspace::getSlug)
                .containsExactly("team", "team");
    }

    @Test
    void rejectsDuplicateSlugForTheSameCreatorBeforePersistence() {
        UUID creator = UUID.randomUUID();
        when(workspaces.existsByCreatorUserIdAndSlug(creator, "team")).thenReturn(true);

        assertThatThrownBy(() -> service.create(creator,
                new CreateWorkspaceRequest("Another team", "team", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You already have a workspace with this slug");

        verify(workspaces, never()).saveAndFlush(any(Workspace.class));
        verify(members, never()).save(any(WorkspaceMember.class));
    }

    @Test
    void mapsConcurrentCreatorSlugConstraintViolationToConflict() {
        UUID creator = UUID.randomUUID();
        DataIntegrityViolationException duplicate = new DataIntegrityViolationException(
                "insert failed", new RuntimeException("constraint uk_workspaces_creator_slug"));
        when(workspaces.saveAndFlush(any(Workspace.class))).thenThrow(duplicate);

        assertThatThrownBy(() -> service.create(creator,
                new CreateWorkspaceRequest("Team", "team", null)))
                .isInstanceOf(ConflictException.class)
                .hasMessage("You already have a workspace with this slug");

        verify(members, never()).save(any(WorkspaceMember.class));
    }

    @Test
    void doesNotMaskUnrelatedIntegrityFailuresAsSlugConflicts() {
        DataIntegrityViolationException failure = new DataIntegrityViolationException("unrelated constraint");
        when(workspaces.saveAndFlush(any(Workspace.class))).thenThrow(failure);

        assertThatThrownBy(() -> service.create(UUID.randomUUID(),
                new CreateWorkspaceRequest("Team", "team", null)))
                .isSameAs(failure);
    }

    private Workspace workspace(UUID id, String name, String slug) {
        Workspace workspace = mock(Workspace.class);
        when(workspace.getId()).thenReturn(id);
        when(workspace.getName()).thenReturn(name);
        when(workspace.getSlug()).thenReturn(slug);
        when(workspace.getStatus()).thenReturn(WorkspaceStatus.ACTIVE);
        return workspace;
    }
}
