package com.assetsphere.modules.workspace.persistence;

import com.assetsphere.modules.workspace.domain.MembershipStatus;
import com.assetsphere.modules.workspace.domain.WorkspaceMember;
import com.assetsphere.modules.workspace.domain.WorkspaceRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMemberRepository extends JpaRepository<WorkspaceMember, UUID> {

    Optional<WorkspaceMember> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);

    List<WorkspaceMember> findByWorkspaceIdAndStatus(UUID workspaceId, MembershipStatus status);

    List<WorkspaceMember> findByUserIdAndStatusOrderByCreatedAtAsc(UUID userId, MembershipStatus status);

    long countByWorkspaceIdAndStatusAndRole(UUID workspaceId, MembershipStatus status, WorkspaceRole role);
}
