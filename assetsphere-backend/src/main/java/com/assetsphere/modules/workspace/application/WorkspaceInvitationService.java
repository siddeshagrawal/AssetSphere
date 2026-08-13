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
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceInvitationDetailsResponse;
import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import com.assetsphere.modules.workspace.api.dto.response.WorkspaceMemberResponse;
import com.assetsphere.modules.workspace.domain.InvitationStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceInvitation;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceInvitationRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceInvitationService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final WorkspaceInvitationRepository invitations;
    private final WorkspaceMemberRepository members;
    private final WorkspaceRepository workspaces;
    private final WorkspaceAuthorization authorization;
    private final ClockProvider clock;
    private final AuditService audit;
    private final WorkspaceInvitationProperties properties;
    private final ObjectProvider<WorkspaceInvitationEmailSender> emailSenders;
    @org.springframework.beans.factory.annotation.Value("${assetsphere.notification.frontend-base-url:http://localhost:5173}")
    private String frontendBaseUrl;

    @Transactional
    public WorkspaceInvitationResponse invite(
            UUID actorUserId,
            String inviterEmail,
            UUID workspaceId,
            InviteWorkspaceMemberRequest request
    ) {
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
                EmailNormalizer.normalize(inviterEmail),
                clock.now().plusSeconds(properties.getInvitationExpirationSeconds())
        ));
        audit.record(
                actorUserId,
                AuditAction.WORKSPACE_MEMBER_INVITED,
                workspaceId,
                "WORKSPACE_INVITATION",
                invitation.getId(),
                "{}"
        );
        String invitationUrl = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/invitations/accept").queryParam("token", rawToken).build().encode().toUriString();
        String deliveryStatus = deliverEmail(invitation, workspaceId, email, invitedRole, invitationUrl);
        return new WorkspaceInvitationResponse(
                invitation.getId(),
                email,
                invitedRole.name(),
                invitation.getExpiresAt(),
                rawToken,
                invitationUrl,
                deliveryStatus
        );
    }

    private String deliverEmail(
            WorkspaceInvitation invitation, UUID workspaceId, String recipientEmail,
            WorkspaceRole role, String invitationUrl) {
        WorkspaceInvitationEmailSender sender = emailSenders.getIfAvailable();
        if (sender == null) return "DISABLED";
        try {
            String workspaceName = workspaces.findById(workspaceId)
                    .map(com.assetsphere.modules.workspace.domain.Workspace::getName).orElse("AssetSphere workspace");
            sender.send(new WorkspaceInvitationEmailSender.InvitationEmail(
                    recipientEmail, invitation.getInvitedByEmail(), workspaceName, role.name(),
                    invitation.getExpiresAt(), invitationUrl));
            return "SENT";
        } catch (RuntimeException exception) {
            log.warn("Workspace invitation email delivery failed workspaceId={} invitationId={}",
                    workspaceId, invitation.getId(), exception);
            return "FAILED";
        }
    }

    @Transactional(readOnly = true)
    public WorkspaceInvitationDetailsResponse validate(String rawToken) {
        WorkspaceInvitation invitation = find(rawToken);
        String status = invitation.isPending() && invitation.isExpired(clock.now())
                ? InvitationStatus.EXPIRED.name()
                : invitation.getStatus().name();
        String workspaceName = workspaces.findById(invitation.getWorkspaceId())
                .map(com.assetsphere.modules.workspace.domain.Workspace::getName)
                .orElse("Unavailable workspace");
        return new WorkspaceInvitationDetailsResponse(
                invitation.getId(), invitation.getWorkspaceId(), workspaceName,
                invitation.getInvitedByEmail(), maskEmail(invitation.getInviteeEmail()), invitation.getRole().name(),
                status, invitation.getExpiresAt());
    }

    @Transactional
    public void decline(String authenticatedEmail, AcceptWorkspaceInvitationRequest request) {
        WorkspaceInvitation invitation = find(request.invitationToken());
        requireRecipient(invitation, authenticatedEmail);
        invitation.decline(clock.now());
    }

    @Transactional
    public WorkspaceMemberResponse accept(
            UUID actorUserId,
            String authenticatedEmail,
            AcceptWorkspaceInvitationRequest request
    ) {
        WorkspaceInvitation invitation = find(request.invitationToken());
        requireRecipient(invitation, authenticatedEmail);

        WorkspaceMember existingMember = members.findByWorkspaceIdAndUserId(invitation.getWorkspaceId(), actorUserId)
                .orElse(null);
        if (invitation.isAccepted()) {
            if (existingMember == null || !existingMember.isActive()) {
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
                null,
                null,
                member.getRole().name(),
                member.getStatus().name(),
                member.getJoinedAt()
        );
    }

    private WorkspaceInvitation find(String rawToken) {
        return invitations.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new BusinessRuleViolationException("Invitation is invalid"));
    }

    private void requireRecipient(WorkspaceInvitation invitation, String authenticatedEmail) {
        if (!invitation.getInviteeEmail().equals(EmailNormalizer.normalize(authenticatedEmail))) {
            throw new AuthorizationDeniedException("Invitation email does not match authenticated user");
        }
    }

    private String maskEmail(String email) {
        int separator = email.indexOf('@');
        if (separator <= 1) {
            return "***" + email.substring(separator);
        }
        return email.substring(0, 1) + "***" + email.substring(separator);
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
