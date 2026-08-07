package com.assetsphere.modules.workspace.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class WorkspaceMemberTests {

    @Test
    void reactivatesRemovedMembershipWithTheInvitedRole() {
        WorkspaceMember member = new WorkspaceMember(
                UUID.randomUUID(), UUID.randomUUID(), WorkspaceRole.VIEWER, UUID.randomUUID(), Instant.now()
        );
        member.remove();

        member.reactivate(WorkspaceRole.MEMBER);

        assertThat(member.isActive()).isTrue();
        assertThat(member.getRole()).isEqualTo(WorkspaceRole.MEMBER);
    }

    @Test
    void removedMembershipIsNotAnActiveMembership() {
        WorkspaceMember member = new WorkspaceMember(
                UUID.randomUUID(), UUID.randomUUID(), WorkspaceRole.MEMBER, UUID.randomUUID(), Instant.now()
        );

        member.remove();

        assertThat(member.isActive()).isFalse();
        assertThat(member.isRemoved()).isTrue();
    }
}
