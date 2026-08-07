package com.assetsphere.modules.asset.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;

@Getter
@Entity
@Table(name = "assets")
public class Asset extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "owner_user_id", nullable = false)
    private UUID ownerUserId;

    @Column(name = "display_name", nullable = false, length = 255)
    private String displayName;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", nullable = false, length = 32)
    private AssetType assetType;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false, length = 32)
    private AssetLifecycleStatus lifecycleStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 32)
    private AssetProcessingStatus processingStatus;

    @Column(name = "latest_version_number", nullable = false)
    private int latestVersionNumber;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Asset() {
    }

    public static Asset initialUpload(
            UUID workspaceId,
            UUID ownerUserId,
            String displayName,
            String description,
            AssetType assetType
    ) {
        Asset asset = new Asset();
        asset.workspaceId = requireIdentifier(workspaceId, "Workspace is required");
        asset.ownerUserId = requireIdentifier(ownerUserId, "Owner is required");
        asset.displayName = requireText(displayName, "Display name is required");
        asset.description = description;
        asset.assetType = requireValue(assetType, "Asset type is required");
        asset.lifecycleStatus = AssetLifecycleStatus.ACTIVE;
        asset.processingStatus = AssetProcessingStatus.UPLOADED;
        asset.latestVersionNumber = 1;
        return asset;
    }

    public void markDeleted(Instant deletedAt) {
        if (lifecycleStatus == AssetLifecycleStatus.DELETED) {
            throw new BusinessRuleViolationException("Asset is already deleted");
        }
        this.lifecycleStatus = AssetLifecycleStatus.DELETED;
        this.deletedAt = requireValue(deletedAt, "Deletion time is required");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
        return value.trim();
    }

    private static UUID requireIdentifier(UUID value, String message) {
        return requireValue(value, message);
    }

    private static <T> T requireValue(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
