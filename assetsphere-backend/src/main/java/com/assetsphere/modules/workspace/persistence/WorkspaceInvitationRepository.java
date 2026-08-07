package com.assetsphere.modules.workspace.persistence;

import com.assetsphere.modules.workspace.domain.InvitationStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceInvitation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceInvitationRepository extends JpaRepository<WorkspaceInvitation, UUID> {

    Optional<WorkspaceInvitation> findByTokenHash(String tokenHash);

    Optional<WorkspaceInvitation> findByWorkspaceIdAndInviteeEmailAndStatus(
            UUID workspaceId,
            String inviteeEmail,
            InvitationStatus status
    );
}
