package com.assetsphere.modules.asset;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;

import com.assetsphere.modules.common.BaseEntity;

@Getter
@Entity
@Table(name = "assets")
public class Asset extends BaseEntity {
    @Column(name = "workspace_id", nullable = false) private UUID workspaceId;
    @Column(name = "owner_user_id", nullable = false) private UUID ownerUserId;
    @Column(name = "display_name", nullable = false, length = 255) private String displayName;
    @Column(length = 2000) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "asset_type", nullable = false, length = 32) private AssetType assetType;
    @Enumerated(EnumType.STRING) @Column(name = "lifecycle_status", nullable = false, length = 32) private AssetLifecycleStatus lifecycleStatus;
    @Enumerated(EnumType.STRING) @Column(name = "processing_status", nullable = false, length = 32) private AssetProcessingStatus processingStatus;
    @Column(name = "latest_version_number", nullable = false) private int latestVersionNumber;

    protected Asset() { }

    public static Asset initialUpload(UUID workspaceId, UUID ownerUserId, String displayName, String description, AssetType assetType) {
        Asset asset = new Asset();
        asset.workspaceId = workspaceId;
        asset.ownerUserId = ownerUserId;
        asset.displayName = displayName;
        asset.description = description;
        asset.assetType = assetType;
        asset.lifecycleStatus = AssetLifecycleStatus.ACTIVE;
        asset.processingStatus = AssetProcessingStatus.UPLOADED;
        asset.latestVersionNumber = 1;
        return asset;
    }
}
