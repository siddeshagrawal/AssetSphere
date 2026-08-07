package com.assetsphere.modules.workspace.domain;

import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(
        name = "workspace_members",
        uniqueConstraints = @UniqueConstraint(name = "uk_workspace_member_user", columnNames = {"workspace_id", "user_id"})
)
public class WorkspaceMember extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MembershipStatus status;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "invited_by_user_id")
    private UUID invitedByUserId;

    protected WorkspaceMember() {
    }

    public WorkspaceMember(UUID workspaceId, UUID userId, WorkspaceRole role, UUID invitedByUserId, Instant joinedAt) {
        this.workspaceId = workspaceId;
        this.userId = userId;
        this.role = role;
        this.invitedByUserId = invitedByUserId;
        this.joinedAt = joinedAt;
        this.status = MembershipStatus.ACTIVE;
    }

    public boolean isActive() {
        return status == MembershipStatus.ACTIVE;
    }

    public boolean isRemoved() {
        return status == MembershipStatus.REMOVED;
    }

    public void changeRole(WorkspaceRole role) {
        this.role = role;
    }

    public void reactivate(WorkspaceRole role) {
        this.role = role;
        this.status = MembershipStatus.ACTIVE;
    }

    public void remove() {
        this.status = MembershipStatus.REMOVED;
    }
}
