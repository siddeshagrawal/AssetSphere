package com.assetsphere.modules.workspace.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.ConflictException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceFacade;
import com.assetsphere.modules.workspace.api.WorkspaceSummary;
import com.assetsphere.modules.workspace.api.dto.request.CreateWorkspaceRequest;
import com.assetsphere.modules.workspace.api.dto.request.UpdateWorkspaceRequest;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceResponse;
import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.Workspace;
import com.assetsphere.modules.workspace.domain.WorkspaceStatus;
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

    private final WorkspaceRepository workspaceRepository;
    private final WorkspaceMemberRepository workspaceMemberRepository;
    private final WorkspaceAuthorization workspaceAuthorization;
    private final ClockProvider clockProvider;
    private final AuditService auditService;

    @Override
    @Transactional
    public WorkspaceSummary createPersonalWorkspace(UUID userId, String displayName) {
        String slug = "personal-" + userId;
        Workspace workspace = workspaceRepository.save(
                Workspace.builder()
                        .name(displayName.trim() + " Workspace")
                        .slug(slug)
                        .status(WorkspaceStatus.ACTIVE)
                        .creatorUserId(userId)
                        .build());
        workspaceMemberRepository.save(
                new WorkspaceMember(workspace.getId(), userId, WorkspaceRole.OWNER, userId, clockProvider.now()));
        auditService.record(userId, AuditAction.WORKSPACE_CREATED, workspace.getId(), "WORKSPACE", workspace.getId(), "{\"personal\":true}");
        return toSummary(workspace, WorkspaceRole.OWNER);
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceSummary> findWorkspacesForUser(UUID userId) {
        List<WorkspaceMember> memberships = workspaceMemberRepository.findByUserIdAndStatusOrderByCreatedAtAsc(userId, MembershipStatus.ACTIVE);
        if (memberships.isEmpty()) {
            return List.of();
        }

        Map<UUID, Workspace> workspacesById = workspaceRepository.findAllById(
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
        String slug = SlugNormalizer.normalize(request.slug());
        if (workspaceRepository.existsByCreatorUserIdAndSlug(actorUserId, slug)) {
            throw duplicateSlug();
        }
        Workspace workspace;
        try {
            workspace = workspaceRepository.saveAndFlush(Workspace.builder()
                    .name(request.name().trim())
                    .slug(slug)
                    .description(request.description())
                    .status(WorkspaceStatus.ACTIVE)
                    .creatorUserId(actorUserId)
                    .build());
        } catch (DataIntegrityViolationException exception) {
            if (isCreatorSlugConflict(exception)) throw duplicateSlug();
            throw exception;
        }
        workspaceMemberRepository.save(new WorkspaceMember(
                workspace.getId(),
                actorUserId,
                WorkspaceRole.OWNER,
                actorUserId,
                clockProvider.now()
        ));
        auditService.record(actorUserId, AuditAction.WORKSPACE_CREATED, workspace.getId(), "WORKSPACE", workspace.getId(), "{}");
        return toResponse(workspace);
    }

    @Transactional(readOnly = true)
    public WorkspaceResponse get(UUID actorUserId, UUID workspaceId) {
        workspaceAuthorization.requireActiveMembership(workspaceId, actorUserId);
        return toResponse(requireWorkspace(workspaceId));
    }

    @Transactional
    public WorkspaceResponse update(UUID actorUserId, UUID workspaceId, UpdateWorkspaceRequest request) {
        workspaceAuthorization.requireAnyRole(workspaceId, actorUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.update(trimToNull(request.name()), request.description());
        auditService.record(actorUserId, AuditAction.WORKSPACE_UPDATED, workspaceId, "WORKSPACE", workspaceId, "{}");
        return toResponse(workspace);
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaceRepository.findById(workspaceId)
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

    private ConflictException duplicateSlug() {
        return new ConflictException("You already have a workspace with this slug");
    }

    private boolean isCreatorSlugConflict(DataIntegrityViolationException exception) {
        Throwable cause = exception;
        while (cause != null) {
            if (cause.getMessage() != null && cause.getMessage().contains("uk_workspaces_creator_slug")) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
