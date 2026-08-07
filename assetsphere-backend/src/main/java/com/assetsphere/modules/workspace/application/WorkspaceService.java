package com.assetsphere.modules.workspace.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;
import com.assetsphere.modules.workspace.api.WorkspaceSummary;
import com.assetsphere.modules.workspace.api.dto.request.CreateWorkspaceRequest;
import com.assetsphere.modules.workspace.api.dto.request.UpdateWorkspaceRequest;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceResponse;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.Workspace;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.domain.SlugNormalizer;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceService implements WorkspaceFacade {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAuthorization authorization;
    private final ClockProvider clock;
    private final AuditService audit;

    @Override
    @Transactional
    public WorkspaceSummary createPersonalWorkspace(UUID userId, String displayName) {
        String slug = "personal-" + userId;
        Workspace workspace = workspaces.save(new Workspace(displayName.trim() + " Workspace", slug, null, userId));
        members.save(new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER, userId, clock.now()));
        audit.record(userId, AuditAction.WORKSPACE_CREATED, workspace.getId(), "WORKSPACE", workspace.getId(), "{\"personal\":true}");
        return toSummary(workspace, WorkspaceRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceSummary> findWorkspacesForUser(UUID userId) {
        List<WorkspaceMember> memberships = members.findByUserIdAndStatusOrderByCreatedAtAsc(userId, MembershipStatus.ACTIVE);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<UUID, Workspace> workspacesById = workspaces.findAllById(
                        memberships.stream().map(WorkspaceMember::getWorkspaceId).toList()
                ).stream()
                .collect(Collectors.toMap(Workspace::getId, Function.identity()));

        return memberships.stream()
                .map(membership -> {
                    Workspace workspace = workspacesById.get(membership.getWorkspaceId());
                    return workspace == null ? null : toSummary(workspace, membership.getRole());
                })
                .filter(Objects::nonNull)
                .toList();
    }

    @Transactional
    public WorkspaceResponse create(UUID actorUserId, CreateWorkspaceRequest request) {
        try {
            Workspace workspace = workspaces.save(new Workspace(
                    request.name().trim(),
                    SlugNormalizer.normalize(request.slug()),
                    request.description(),
                    actorUserId
            ));
            members.save(new WorkspaceMember(
                    workspace.getId(),
                    actorUserId,
                    WorkspaceRole.OWNER,
                    actorUserId,
                    clock.now()
            ));
            audit.record(actorUserId, AuditAction.WORKSPACE_CREATED, workspace.getId(), "WORKSPACE", workspace.getId(), "{}");
            return toResponse(workspace);
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessRuleViolationException("Workspace slug is already in use");
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID actorUserId, UUID workspaceId) {
        authorization.requireActiveMembership(workspaceId, actorUserId);
        return toResponse(requireWorkspace(workspaceId));
    }

    @Transactional
    public WorkspaceResponse update(UUID actorUserId, UUID workspaceId, UpdateWorkspaceRequest request) {
        authorization.requireAnyRole(workspaceId, actorUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.update(trimToNull(request.name()), request.description());
        audit.record(actorUserId, AuditAction.WORKSPACE_UPDATED, workspaceId, "WORKSPACE", workspaceId, "{}");
        return toResponse(workspace);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Workspace not found"));
    }

    private WorkspaceSummary toSummary(Workspace workspace, WorkspaceRole role) {
        return new WorkspaceSummary(workspace.getId(), workspace.getName(), workspace.getSlug(), role.name());
    }

    private WorkspaceResponse toResponse(Workspace workspace) {
        return new WorkspaceResponse(
                workspace.getId(),
                workspace.getName(),
                workspace.getSlug(),
                workspace.getDescription(),
                workspace.getStatus().name()
        );
    }

    private String trimToNull(String value) {
        return value == null ? null : value.trim();
    }
}
