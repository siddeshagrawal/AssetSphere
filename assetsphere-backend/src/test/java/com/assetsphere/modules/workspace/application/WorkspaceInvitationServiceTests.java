package com.assetsphere.modules.workspace.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.dto.request.AcceptWorkspaceInvitationRequest;
import com.assetsphere.modules.workspace.domain.WorkspaceInvitation;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceInvitationRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationServiceTests {

    private final WorkspaceInvitationRepository invitations = mock(WorkspaceInvitationRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Instant now = Instant.parse("2026-08-07T00:00:00Z");
    private final WorkspaceInvitationService service = new WorkspaceInvitationService(
            invitations,
            members,
            mock(WorkspaceAuthorization.class),
            (ClockProvider) () -> now,
            audit
    );

    @Test
    void reactivatesRemovedMemberWithoutCreatingAnotherMembership() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String rawToken = "valid-invitation-token";
        WorkspaceInvitation invitation = invitation(workspaceId, rawToken, now.plusSeconds(60));
        WorkspaceMember removedMember = new WorkspaceMember(workspaceId, userId, WorkspaceRole.VIEWER, UUID.randomUUID(), now.minusSeconds(60));
        removedMember.remove();
        when(invitations.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(invitation));
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(removedMember));

        var response = service.accept(userId, "member@example.com", new AcceptWorkspaceInvitationRequest(rawToken));

        assertThat(response.role()).isEqualTo(WorkspaceRole.MEMBER.name());
        assertThat(removedMember.isActive()).isTrue();
        assertThat(removedMember.getRole()).isEqualTo(WorkspaceRole.MEMBER);
        assertThat(invitation.isAccepted()).isTrue();
        verify(members, never()).save(any());
    }

    @Test
    void rejectsInvitationWhenAuthenticatedEmailDoesNotMatch() {
        UUID workspaceId = UUID.randomUUID();
        String rawToken = "email-mismatch-token";
        when(invitations.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(invitation(workspaceId, rawToken, now.plusSeconds(60))));

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), "other@example.com", new AcceptWorkspaceInvitationRequest(rawToken)))
                .isInstanceOf(AuthorizationDeniedException.class)
                .hasMessage("Invitation email does not match authenticated user");
    }

    @Test
    void rejectsExpiredInvitationBeforeCreatingMembership() {
        UUID workspaceId = UUID.randomUUID();
        String rawToken = "expired-token";
        when(invitations.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(invitation(workspaceId, rawToken, now.minusSeconds(1))));
        when(members.findByWorkspaceIdAndUserId(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.accept(UUID.randomUUID(), "member@example.com", new AcceptWorkspaceInvitationRequest(rawToken)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Invitation has expired");
        verify(members, never()).save(any());
    }

    @Test
    void repeatedAcceptanceReturnsExistingMembershipWithoutCreatingDuplicate() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String rawToken = "replay-token";
        WorkspaceInvitation invitation = invitation(workspaceId, rawToken, now.plusSeconds(60));
        WorkspaceMember member = new WorkspaceMember(workspaceId, userId, WorkspaceRole.MEMBER, UUID.randomUUID(), now);
        invitation.accept(now);
        when(invitations.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(invitation));
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(member));

        service.accept(userId, "member@example.com", new AcceptWorkspaceInvitationRequest(rawToken));

        verify(members, never()).save(any());
        verify(audit, never()).record(any(), any(), any(), any(), any(), any());
    }

    private WorkspaceInvitation invitation(UUID workspaceId, String rawToken, Instant expiresAt) {
        return new WorkspaceInvitation(
                workspaceId,
                "member@example.com",
                WorkspaceRole.MEMBER,
                hash(rawToken),
                UUID.randomUUID(),
                expiresAt
        );
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
