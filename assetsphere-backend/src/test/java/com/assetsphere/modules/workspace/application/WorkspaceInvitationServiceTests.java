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
import com.assetsphere.modules.workspace.api.dto.request.InviteWorkspaceMemberRequest;
import com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import com.assetsphere.modules.workspace.domain.Workspace;
import com.assetsphere.modules.workspace.domain.WorkspaceStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceInvitation;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import com.assetsphere.modules.workspace.persistence.WorkspaceInvitationRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceMemberRepository;
import com.assetsphere.modules.workspace.persistence.WorkspaceRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class WorkspaceInvitationServiceTests {

    private final WorkspaceInvitationRepository invitations = mock(WorkspaceInvitationRepository.class);
    private final WorkspaceMemberRepository members = mock(WorkspaceMemberRepository.class);
    private final AuditService audit = mock(AuditService.class);
    private final Instant now = Instant.parse("2026-08-07T00:00:00Z");
    private final WorkspaceInvitationProperties properties = new WorkspaceInvitationProperties();
    private final WorkspaceInvitationService service = new WorkspaceInvitationService(
            invitations,
            members,
            mock(WorkspaceRepository.class),
            mock(WorkspaceAuthorization.class),
            (ClockProvider) () -> now,
            audit,
            properties,
            new DefaultListableBeanFactory().getBeanProvider(com.assetsphere.modules.workspace.api.WorkspaceInvitationEmailSender.class)
    );

    @Test
    void invitationEmailDeliveryIsReportedWithoutExposingProviderDetails() {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WorkspaceInvitationEmailSender sender = mock(WorkspaceInvitationEmailSender.class);
        @SuppressWarnings("unchecked") ObjectProvider<WorkspaceInvitationEmailSender> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(sender);
        WorkspaceInvitationRepository invitationRepository = mock(WorkspaceInvitationRepository.class);
        WorkspaceMemberRepository memberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMember inviter = mock(WorkspaceMember.class);
        when(inviter.getRole()).thenReturn(WorkspaceRole.OWNER);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, actorId)).thenReturn(Optional.of(inviter));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(Workspace.builder()
                .name("Operations").slug("operations").status(WorkspaceStatus.ACTIVE).creatorUserId(actorId).build()));
        WorkspaceInvitationService emailService = new WorkspaceInvitationService(invitationRepository, memberRepository,
                workspaceRepository, mock(WorkspaceAuthorization.class), (ClockProvider) () -> now, audit, properties, providers);
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://app.assetsphere.example");

        var response = emailService.invite(actorId, "owner@example.com", workspaceId,
                new InviteWorkspaceMemberRequest("member@example.com", WorkspaceRoleView.MEMBER));

        assertThat(response.emailDeliveryStatus()).isEqualTo("SENT");
        assertThat(response.invitationUrl()).startsWith("https://app.assetsphere.example/invitations/accept?token=");
        verify(sender).send(any());
    }

    @Test
    void emailFailureKeepsManualInvitationAvailable() {
        UUID workspaceId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        WorkspaceInvitationEmailSender sender = mock(WorkspaceInvitationEmailSender.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("smtp unavailable")).when(sender).send(any());
        @SuppressWarnings("unchecked") ObjectProvider<WorkspaceInvitationEmailSender> providers = mock(ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(sender);
        WorkspaceInvitationRepository invitationRepository = mock(WorkspaceInvitationRepository.class);
        WorkspaceMemberRepository memberRepository = mock(WorkspaceMemberRepository.class);
        WorkspaceRepository workspaceRepository = mock(WorkspaceRepository.class);
        WorkspaceMember inviter = mock(WorkspaceMember.class);
        when(inviter.getRole()).thenReturn(WorkspaceRole.OWNER);
        when(memberRepository.findByWorkspaceIdAndUserId(workspaceId, actorId)).thenReturn(Optional.of(inviter));
        when(invitationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(workspaceRepository.findById(workspaceId)).thenReturn(Optional.of(Workspace.builder()
                .name("Operations").slug("operations").status(WorkspaceStatus.ACTIVE).creatorUserId(actorId).build()));
        WorkspaceInvitationService emailService = new WorkspaceInvitationService(invitationRepository, memberRepository,
                workspaceRepository, mock(WorkspaceAuthorization.class), (ClockProvider) () -> now, audit, properties, providers);
        ReflectionTestUtils.setField(emailService, "frontendBaseUrl", "https://app.assetsphere.example");

        var response = emailService.invite(actorId, "owner@example.com", workspaceId,
                new InviteWorkspaceMemberRequest("member@example.com", WorkspaceRoleView.MEMBER));

        assertThat(response.emailDeliveryStatus()).isEqualTo("FAILED");
        assertThat(response.invitationToken()).isNotBlank();
        assertThat(response.invitationUrl()).isNotBlank();
    }

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

    @Test
    void repeatedAcceptanceDoesNotRestoreAFormerMember() {
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String rawToken = "removed-replay-token";
        WorkspaceInvitation invitation = invitation(workspaceId, rawToken, now.plusSeconds(60));
        WorkspaceMember member = new WorkspaceMember(workspaceId, userId, WorkspaceRole.MEMBER, UUID.randomUUID(), now);
        member.remove();
        invitation.accept(now);
        when(invitations.findByTokenHash(hash(rawToken))).thenReturn(Optional.of(invitation));
        when(members.findByWorkspaceIdAndUserId(workspaceId, userId)).thenReturn(Optional.of(member));

        assertThatThrownBy(() -> service.accept(userId, "member@example.com", new AcceptWorkspaceInvitationRequest(rawToken)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Invitation was already accepted");
        assertThat(member.isRemoved()).isTrue();
    }

    private WorkspaceInvitation invitation(UUID workspaceId, String rawToken, Instant expiresAt) {
        return new WorkspaceInvitation(
                workspaceId,
                "member@example.com",
                WorkspaceRole.MEMBER,
                hash(rawToken),
                UUID.randomUUID(),
                "owner@example.com",
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
