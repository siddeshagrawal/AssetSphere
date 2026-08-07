package com.assetsphere.modules.workspace.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "workspace_invitations")
public class WorkspaceInvitation extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "invitee_email", nullable = false, length = 320)
    private String inviteeEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "invited_by_user_id", nullable = false)
    private UUID invitedByUserId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationStatus status;

    protected WorkspaceInvitation() {
    }

    public WorkspaceInvitation(
            UUID workspaceId,
            String inviteeEmail,
            WorkspaceRole role,
            String tokenHash,
            UUID invitedByUserId,
            Instant expiresAt
    ) {
        this.workspaceId = workspaceId;
        this.inviteeEmail = inviteeEmail;
        this.role = role;
        this.tokenHash = tokenHash;
        this.invitedByUserId = invitedByUserId;
        this.expiresAt = expiresAt;
        this.status = InvitationStatus.PENDING;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    public boolean isAccepted() {
        return status == InvitationStatus.ACCEPTED;
    }

    public void accept(Instant now) {
        if (!isPending()) {
            throw new BusinessRuleViolationException("Invitation is not pending");
        }
        if (isExpired(now)) {
            expire();
            throw new BusinessRuleViolationException("Invitation has expired");
        }
        status = InvitationStatus.ACCEPTED;
        acceptedAt = now;
    }

    public void expire() {
        if (isPending()) {
            status = InvitationStatus.EXPIRED;
        }
    }
}
