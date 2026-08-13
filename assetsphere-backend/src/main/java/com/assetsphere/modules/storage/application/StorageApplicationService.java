package com.assetsphere.modules.storage.application;

import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.storage.api.AssetStorage;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.storage.domain.StorageObject;
import com.assetsphere.modules.storage.persistence.StorageObjectRepository;

import java.io.InputStream;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.AuditorAware;

@Service
@RequiredArgsConstructor
class StorageApplicationService implements StorageFacade {

    private final StorageObjectRepository storageObjectRepository;
    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final AssetStorage assetStorage;
    private final ClockProvider clock;
    private final AuditorAware<UUID> auditorAware;

    @Override
    public PreparedStorageObject prepare(PrepareStorageObjectCommand command) {
        validate(command);

        return storageObjectRepository.findByWorkspaceIdAndChecksumSha256(command.workspaceId(), command.checksumSha256())
                .map(this::existingPreparation)
                .orElseGet(() -> storeNewPhysicalObject(command));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public StorageObjectReference attach(PreparedStorageObject prepared) {
        OffsetDateTime now = clock.now().atOffset(ZoneOffset.UTC);
        UUID candidateId = UUID.randomUUID();
        UUID actorId = auditorAware.getCurrentAuditor().orElse(new UUID(0L, 0L));
        MapSqlParameterSource parameters = new MapSqlParameterSource()
                .addValue("id", candidateId)
                .addValue("workspaceId", prepared.workspaceId())
                .addValue("checksum", prepared.checksumSha256())
                .addValue("objectKey", prepared.canonicalObjectKey())
                .addValue("provider", prepared.storageProvider())
                .addValue("fileSize", prepared.fileSize())
                .addValue("mimeType", prepared.mimeType())
                .addValue("actorId", actorId)
                .addValue("now", now, Types.TIMESTAMP_WITH_TIMEZONE);

        return jdbcTemplate.queryForObject("""
                INSERT INTO storage_objects (
                    id, workspace_id, checksum_sha256, object_key, storage_provider,
                    file_size, mime_type, reference_count, created_at, updated_at,
                    created_by, updated_by, version
                ) VALUES (
                    :id, :workspaceId, :checksum, :objectKey, :provider,
                    :fileSize, :mimeType, 1, :now, :now, :actorId, :actorId, 0
                )
                ON CONFLICT (workspace_id, checksum_sha256)
                DO UPDATE SET
                    reference_count = storage_objects.reference_count + 1,
                    updated_at = EXCLUDED.updated_at,
                    updated_by = EXCLUDED.updated_by,
                    version = storage_objects.version + 1
                RETURNING id, object_key, (xmax = 0) AS created
                """, parameters, (resultSet, rowNum) -> new StorageObjectReference(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("object_key"),
                resultSet.getBoolean("created")
        ));
    }

    @Override
    public void materializeCanonicalObject(PreparedStorageObject prepared) {
        if (!prepared.temporaryObjectStored()) {
            return;
        }
        try {
            assetStorage.copy(prepared.temporaryObjectKey(), prepared.canonicalObjectKey());
            assetStorage.delete(prepared.temporaryObjectKey());
        } catch (RuntimeException exception) {
            deleteCanonicalObject(prepared.canonicalObjectKey(), exception);
            throw exception;
        }
    }

    @Override
    public void discardTemporaryObject(PreparedStorageObject prepared) {
        if (prepared.temporaryObjectStored()) {
            assetStorage.delete(prepared.temporaryObjectKey());
        }
    }

    @Override
    public void rollbackCanonicalMaterialization(PreparedStorageObject prepared) {
        if (prepared.temporaryObjectStored()) {
            assetStorage.delete(prepared.canonicalObjectKey());
        }
    }

    @Override
    public void compensate(PreparedStorageObject prepared) {
        discardTemporaryObject(prepared);
    }

    @Override
    public InputStream open(String objectKey) {
        if (isBlank(objectKey)) {
            throw new InvalidRequestException("Storage object key is required");
        }
        return assetStorage.open(objectKey);
    }

    @Override
    @Transactional(readOnly = true)
    public StoredObjectContent open(UUID workspaceId, UUID storageObjectId) {
        StorageObject storageObject = storageObjectRepository.findById(storageObjectId)
                .filter(candidate -> candidate.getWorkspaceId().equals(workspaceId))
                .orElseThrow(() -> new ResourceNotFoundException("Stored asset content not found"));
        return new StoredObjectContent(
                assetStorage.open(storageObject.getObjectKey()),
                storageObject.getMimeType(),
                storageObject.getFileSize()
        );
    }

    private PreparedStorageObject existingPreparation(StorageObject storageObject) {
        return new PreparedStorageObject(
                storageObject.getWorkspaceId(),
                storageObject.getChecksumSha256(),
                storageObject.getObjectKey(),
                null,
                storageObject.getStorageProvider().name(),
                storageObject.getMimeType(),
                storageObject.getFileSize(),
                false,
                true
        );
    }

    private PreparedStorageObject storeNewPhysicalObject(PrepareStorageObjectCommand command) {
        UUID uploadAttemptId = UUID.randomUUID();
        String temporaryObjectKey = "workspaces/%s/tmp/%s/%s".formatted(
                command.workspaceId(), uploadAttemptId, command.checksumSha256()
        );
        String canonicalObjectKey = "workspaces/%s/objects/%s".formatted(
                command.workspaceId(), command.checksumSha256()
        );
        assetStorage.store(new AssetStorage.StoreAssetCommand(
                temporaryObjectKey,
                command.content(),
                command.fileSize(),
                command.mimeType()
        ));
        return new PreparedStorageObject(
                command.workspaceId(),
                command.checksumSha256(),
                canonicalObjectKey,
                temporaryObjectKey,
                "MINIO",
                command.mimeType(),
                command.fileSize(),
                true,
                false
        );
    }

    private void validate(PrepareStorageObjectCommand command) {
        if (command == null || command.workspaceId() == null || command.content() == null
                || command.fileSize() <= 0 || isBlank(command.checksumSha256()) || isBlank(command.mimeType())) {
            throw new InvalidRequestException("Storage preparation details are invalid");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private void deleteCanonicalObject(String canonicalObjectKey, RuntimeException originalException) {
        try {
            assetStorage.delete(canonicalObjectKey);
        } catch (RuntimeException cleanupException) {
            originalException.addSuppressed(cleanupException);
        }
    }
}
