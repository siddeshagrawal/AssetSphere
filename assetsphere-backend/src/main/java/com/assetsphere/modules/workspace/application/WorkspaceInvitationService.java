package com.assetsphere.modules.workspace.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.text.EmailNormalizer;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.dto.request.AcceptWorkspaceInvitationRequest;
import com.assetsphere.modules.workspace.api.dto.request.InviteWorkspaceMemberRequest;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceInvitationResponse;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceMemberResponse;
import com.assetsphere.modules.workspace.domain.InvitationStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceInvitation;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceInvitationRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class WorkspaceInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceMemberRepository members;
    private final WorkspaceAuthorization authorization;
    private final ClockProvider clock;
    private final AuditService audit;

    @Value("${assetsphere.security.invitation-expiration-seconds}")
    private long invitationExpirationSeconds;

    @Transactional
    public WorkspaceInvitationResponse invite(UUID actorUserId, UUID workspaceId, InviteWorkspaceMemberRequest request) {
        authorization.requireAnyRole(workspaceId, actorUserId, WorkspaceRole.OWNER, WorkspaceRole.ADMIN);
        WorkspaceMember inviter = members.findByWorkspaceIdAndUserId(workspaceId, actorUserId).orElseThrow();
        WorkspaceRole invitedRole = WorkspaceRole.valueOf(request.role().name());

        if (invitedRole == WorkspaceRole.OWNER && inviter.getRole() != WorkspaceRole.OWNER) {
            throw new AuthorizationDeniedException("Only an owner may grant the owner role");
        }

        String email = EmailNormalizer.normalize(request.email());
        invitations.findByWorkspaceIdAndInviteeEmailAndStatus(workspaceId, email, InvitationStatus.PENDING)
                .ifPresent(invitation -> {
                    throw new BusinessRuleViolationException("A pending invitation already exists");
                });

        String rawToken = newInvitationToken();
        WorkspaceInvitation invitation = invitations.save(new WorkspaceInvitation(
                workspaceId,
                email,
                invitedRole,
                hash(rawToken),
                actorUserId,
                clock.now().plusSeconds(invitationExpirationSeconds)
        ));
        audit.record(
                actorUserId,
                AuditAction.WORKSPACE_MEMBER_INVITED,
                workspaceId,
                "WORKSPACE_INVITATION",
                invitation.getId(),
                "{}"
        );
        return new WorkspaceInvitationResponse(
                invitation.getId(),
                email,
                invitedRole.name(),
                invitation.getExpiresAt(),
                rawToken
        );
    }

    @Transactional
    public WorkspaceMemberResponse accept(
            UUID actorUserId,
            String authenticatedEmail,
            AcceptWorkspaceInvitationRequest request
    ) {
        WorkspaceInvitation invitation = invitations.findByTokenHash(hash(request.invitationToken()))
                .orElseThrow(() -> new BusinessRuleViolationException("Invitation is invalid"));

        if (!invitation.getInviteeEmail().equals(EmailNormalizer.normalize(authenticatedEmail))) {
            throw new AuthorizationDeniedException("Invitation email does not match authenticated user");
        }

        WorkspaceMember existingMember = members.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), actorUserId)
                .orElse(null);
        if (invitation.isAccepted()) {
            if (existingMember == null) {
                throw new BusinessRuleViolationException("Invitation was already accepted");
            }
            return toResponse(existingMember);
        }

        Instant now = clock.now();
        invitation.accept(now);

        WorkspaceMember member;
        if (existingMember == null) {
            member = members.save(new WorkspaceMember(
                    invitation.getWorkspaceId(),
                    actorUserId,
                    invitation.getRole(),
                    invitation.getInvitedByUserId(),
                    now
            ));
        } else if (existingMember.isRemoved()) {
            existingMember.reactivate(invitation.getRole());
            member = existingMember;
        } else {
            member = existingMember;
        }

        audit.record(
                actorUserId,
                AuditAction.WORKSPACE_INVITATION_ACCEPTED,
                invitation.getWorkspaceId(),
                "WORKSPACE_INVITATION",
                invitation.getId(),
                "{}"
        );
        return toResponse(member);
    }

    private WorkspaceMemberResponse toResponse(WorkspaceMember member) {
        return new WorkspaceMemberResponse(
                member.getId(),
                member.getUserId(),
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }

    private String newInvitationToken() {
        byte[] bytes = new byte[48];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String rawToken) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
