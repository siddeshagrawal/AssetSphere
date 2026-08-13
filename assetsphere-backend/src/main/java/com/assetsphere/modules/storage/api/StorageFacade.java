package com.assetsphere.modules.storage.api;

import java.io.InputStream;
import java.util.UUID;

public interface StorageFacade {

    PreparedStorageObject prepare(PrepareStorageObjectCommand command);

    StorageObjectReference attach(PreparedStorageObject preparedStorageObject);

    void materializeCanonicalObject(PreparedStorageObject preparedStorageObject);

    void discardTemporaryObject(PreparedStorageObject preparedStorageObject);

    void rollbackCanonicalMaterialization(PreparedStorageObject preparedStorageObject);

    void compensate(PreparedStorageObject preparedStorageObject);

    InputStream open(String objectKey);

    StoredObjectContent open(UUID workspaceId, UUID storageObjectId);

    record PrepareStorageObjectCommand(
            UUID workspaceId,
            String checksumSha256,
            String mimeType,
            long fileSize,
            InputStream content
    ) {
    }

    record PreparedStorageObject(
            UUID workspaceId,
            String checksumSha256,
            String canonicalObjectKey,
            String temporaryObjectKey,
            String storageProvider,
            String mimeType,
            long fileSize,
            boolean temporaryObjectStored,
            boolean existingBinary
    ) {
    }

    record StorageObjectReference(UUID storageObjectId, String objectKey, boolean created) {
    }

    record StoredObjectContent(InputStream content, String mimeType, long fileSize) {
    }
}
