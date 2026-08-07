package com.assetsphere.modules.storage.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "storage_objects")
public class StorageObject extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "object_key", nullable = false, length = 1024)
    private String objectKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "storage_provider", nullable = false, length = 32)
    private StorageProvider storageProvider;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "mime_type", nullable = false, length = 255)
    private String mimeType;

    @Column(name = "reference_count", nullable = false)
    private int referenceCount;

    protected StorageObject() {
    }

    public static StorageObject create(UUID workspaceId, String checksum, String objectKey,
                                       long fileSize, String mimeType) {
        StorageObject storageObject = new StorageObject();
        storageObject.workspaceId = requireValue(workspaceId, "Workspace is required");
        storageObject.checksumSha256 = requireText(checksum, "Checksum is required");
        storageObject.objectKey = requireText(objectKey, "Object key is required");
        if (fileSize <= 0) {
            throw new BusinessRuleViolationException("File size must be positive");
        }
        storageObject.fileSize = fileSize;
        storageObject.mimeType = requireText(mimeType, "MIME type is required");
        storageObject.storageProvider = StorageProvider.MINIO;
        storageObject.referenceCount = 1;
        return storageObject;
    }

    public void incrementReferenceCount() {
        if (referenceCount == Integer.MAX_VALUE) {
            throw new BusinessRuleViolationException("Storage object reference count limit reached");
        }
        referenceCount++;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
        return value.trim();
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
