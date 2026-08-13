package com.assetsphere.infrastructure.kafka;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.common.exception.AuthorizationDeniedException;
import com.assetsphere.modules.common.security.CurrentUser;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DltOperationsControllerTests {
    @Test
    void inspectionRequiresWorkspaceOperatorAuthorization() {
        DltOperationsService operations = mock(DltOperationsService.class);
        WorkspaceAccessFacade access = mock(WorkspaceAccessFacade.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(userId, "operator@example.com"));
        org.mockito.Mockito.doThrow(new AuthorizationDeniedException("Access is denied"))
                .when(access).requireRole(workspaceId, userId, Set.of(WorkspaceRoleView.OWNER, WorkspaceRoleView.ADMIN));

        var controller = new DltOperationsController(operations, access, users, mock(ClockProvider.class));

        assertThatThrownBy(() -> controller.inspect(workspaceId, 50))
                .isInstanceOf(AuthorizationDeniedException.class);
        verifyNoInteractions(operations);
    }

    @Test
    void replayDelegatesOnlyAfterWorkspaceAuthorization() {
        DltOperationsService operations = mock(DltOperationsService.class);
        WorkspaceAccessFacade access = mock(WorkspaceAccessFacade.class);
        CurrentUserProvider users = mock(CurrentUserProvider.class);
        UUID workspaceId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(users.requireCurrentUser()).thenReturn(new CurrentUser(userId, "operator@example.com"));

        var controller = new DltOperationsController(operations, access, users, mock(ClockProvider.class));
        controller.replay(workspaceId, "assets.uploaded.v1.DLT", 0, 7);

        verify(access).requireRole(workspaceId, userId, Set.of(WorkspaceRoleView.OWNER, WorkspaceRoleView.ADMIN));
        verify(operations).replay(workspaceId, userId, "assets.uploaded.v1.DLT", 0, 7);
    }
}
