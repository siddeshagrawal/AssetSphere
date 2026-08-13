package com.assetsphere.modules.storage.application;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.storage.api.AssetStorage;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.storage.persistence.StorageObjectRepository;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StorageApplicationServiceTests {

    @Mock private StorageObjectRepository storageObjects;
    @Mock private AssetStorage assetStorage;
    @Mock private NamedParameterJdbcTemplate jdbcTemplate;

    @Test
    void preparesRequestOwnedTemporaryObjectAndStableCanonicalKey() {
        UUID workspaceId = UUID.randomUUID();
        when(storageObjects.findByWorkspaceIdAndChecksumSha256(workspaceId, "checksum")).thenReturn(java.util.Optional.empty());

        StorageFacade.PreparedStorageObject prepared = service().prepare(command(workspaceId));

        assertThat(prepared.canonicalObjectKey()).isEqualTo("workspaces/%s/objects/checksum".formatted(workspaceId));
        assertThat(prepared.temporaryObjectKey()).startsWith("workspaces/%s/tmp/".formatted(workspaceId));
        assertThat(prepared.temporaryObjectKey()).endsWith("/checksum");
        assertThat(prepared.temporaryObjectStored()).isTrue();
        ArgumentCaptor<AssetStorage.StoreAssetCommand> command = ArgumentCaptor.forClass(AssetStorage.StoreAssetCommand.class);
        verify(assetStorage).store(command.capture());
        assertThat(command.getValue().objectKey()).isEqualTo(prepared.temporaryObjectKey());
    }

    @Test
    void losingRequestDeletesOnlyItsOwnTemporaryObject() {
        UUID workspaceId = UUID.randomUUID();
        StorageFacade.PreparedStorageObject winner = prepared(workspaceId, "workspaces/winner-canonical", "workspaces/tmp/winner");
        StorageFacade.PreparedStorageObject loser = prepared(workspaceId, "workspaces/winner-canonical", "workspaces/tmp/loser");

        service().compensate(loser);

        verify(assetStorage).delete(loser.temporaryObjectKey());
        verify(assetStorage, never()).delete(winner.canonicalObjectKey());
    }

    @Test
    void concurrentFirstPreparationsUseDistinctTemporaryKeysForOneCanonicalKey() {
        UUID workspaceId = UUID.randomUUID();
        when(storageObjects.findByWorkspaceIdAndChecksumSha256(workspaceId, "checksum")).thenReturn(java.util.Optional.empty());

        StorageFacade.PreparedStorageObject first = service().prepare(command(workspaceId));
        StorageFacade.PreparedStorageObject second = service().prepare(command(workspaceId));

        assertThat(first.canonicalObjectKey()).isEqualTo(second.canonicalObjectKey());
        assertThat(first.temporaryObjectKey()).isNotEqualTo(second.temporaryObjectKey());
        service().materializeCanonicalObject(first);
        service().compensate(second);
        verify(assetStorage).delete(first.temporaryObjectKey());
        verify(assetStorage).delete(second.temporaryObjectKey());
        verify(assetStorage, never()).delete(first.canonicalObjectKey());
    }

    @Test
    void existingCanonicalPreparationNeverDeletesPhysicalStorageDuringCompensation() {
        StorageFacade.PreparedStorageObject existing = new StorageFacade.PreparedStorageObject(
                UUID.randomUUID(), "checksum", "workspaces/canonical", null, "MINIO", "application/pdf", 1, false, true
        );

        service().compensate(existing);

        verify(assetStorage, never()).delete(existing.canonicalObjectKey());
    }

    @Test
    void materializesCanonicalObjectThenRemovesOnlyTemporaryObject() {
        StorageFacade.PreparedStorageObject prepared = prepared(UUID.randomUUID(), "workspaces/canonical", "workspaces/tmp/request");

        service().materializeCanonicalObject(prepared);

        verify(assetStorage).copy(prepared.temporaryObjectKey(), prepared.canonicalObjectKey());
        verify(assetStorage).delete(prepared.temporaryObjectKey());
        verify(assetStorage, never()).delete(prepared.canonicalObjectKey());
    }

    private StorageApplicationService service() {
        ClockProvider clock = () -> Instant.parse("2026-08-07T00:00:00Z");
        return new StorageApplicationService(storageObjects, jdbcTemplate, assetStorage, clock,
                () -> Optional.of(UUID.randomUUID()));
    }

    private StorageFacade.PrepareStorageObjectCommand command(UUID workspaceId) {
        return new StorageFacade.PrepareStorageObjectCommand(
                workspaceId, "checksum", "application/pdf", 1, new ByteArrayInputStream(new byte[] {1})
        );
    }

    private StorageFacade.PreparedStorageObject prepared(UUID workspaceId, String canonicalKey, String temporaryKey) {
        return new StorageFacade.PreparedStorageObject(
                workspaceId, "checksum", canonicalKey, temporaryKey, "MINIO", "application/pdf", 1, true, false
        );
    }
}
