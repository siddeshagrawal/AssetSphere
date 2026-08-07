package com.assetsphere.modules.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceInvitationTests {

    @Test
    void acceptsPendingInvitationAndRecordsAcceptanceTime() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        WorkspaceInvitation invitation = invitation(now.plusSeconds(60));

        invitation.accept(now);

        assertThat(invitation.isAccepted()).isTrue();
        assertThat(invitation.getAcceptedAt()).isEqualTo(now);
    }

    @Test
    void rejectsExpiredInvitation() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        WorkspaceInvitation invitation = invitation(now.minusSeconds(1));

        assertThatThrownBy(() -> invitation.accept(now))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Invitation has expired");
        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.EXPIRED);
    }

    @Test
    void rejectsRepeatedAcceptance() {
        Instant now = Instant.parse("2026-08-07T00:00:00Z");
        WorkspaceInvitation invitation = invitation(now.plusSeconds(60));
        invitation.accept(now);

        assertThatThrownBy(() -> invitation.accept(now.plusSeconds(1)))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessage("Invitation is not pending");
    }

    private WorkspaceInvitation invitation(Instant expiresAt) {
        return new WorkspaceInvitation(
                UUID.randomUUID(),
                "member@example.com",
                WorkspaceRole.MEMBER,
                "hash",
                UUID.randomUUID(),
                expiresAt
        );
    }
}
