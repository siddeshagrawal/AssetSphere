package com.assetsphere.modules.asset.domain;

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
@Table(name = "asset_versions")
public class AssetVersion extends BaseEntity {

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "original_filename", nullable = false, length = 512)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false, length = 255)
    private String mimeType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "checksum_sha256", nullable = false, length = 64)
    private String checksumSha256;

    @Column(name = "storage_object_id", nullable = false)
    private UUID storageObjectId;

    @Column(name = "uploaded_by_user_id", nullable = false)
    private UUID uploadedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private AssetProcessingStatus processingStatus;

    protected AssetVersion() {
    }

    public static AssetVersion initial(
            UUID assetId,
            String filename,
            String mimeType,
            long size,
            String checksum,
            UUID storageObjectId,
            UUID uploadedByUserId
    ) {
        AssetVersion version = new AssetVersion();
        version.assetId = requireValue(assetId, "Asset is required");
        version.versionNumber = 1;
        version.originalFilename = requireText(filename, "Filename is required");
        version.mimeType = requireText(mimeType, "MIME type is required");
        if (size <= 0) {
            throw new BusinessRuleViolationException("File size must be positive");
        }
        version.fileSize = size;
        version.checksumSha256 = requireText(checksum, "Checksum is required");
        version.storageObjectId = requireValue(storageObjectId, "Storage object is required");
        version.uploadedByUserId = requireValue(uploadedByUserId, "Uploader is required");
        version.processingStatus = AssetProcessingStatus.UPLOADED;
        return version;
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
