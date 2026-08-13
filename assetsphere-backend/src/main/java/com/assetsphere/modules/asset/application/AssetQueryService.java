package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.asset.api.dto.response.AssetResponse;
import com.assetsphere.modules.asset.api.dto.response.AssetVersionResponse;
import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetVersion;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.web.PageResponse;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.storage.api.StorageFacade;
import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssetQueryService implements AssetReadFacade {

    private static final int MAX_PAGE_SIZE = 100;

    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetRepository assets;
    private final AssetVersionRepository assetVersions;
    private final AssetMetadataCache assetMetadataCache;
    private final StorageFacade storageFacade;

    @Transactional(readOnly = true)
    public AssetResponse get(UUID userId, UUID workspaceId, UUID assetId) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        return getMetadata(workspaceId, assetId).toResponse();
    }

    @Override
    @Transactional(readOnly = true)
    public AssetMetadataSnapshot getMetadata(UUID workspaceId, UUID assetId) {
        var cached = assetMetadataCache.get(workspaceId, assetId).map(AssetMetadataSnapshot::toResponse);
        if (cached.isPresent()) {
            AssetResponse response = cached.get();
            return new AssetMetadataSnapshot(
                    response.assetId(), response.assetVersionId(), response.workspaceId(), response.originalFilename(),
                    response.displayName(), response.description(), response.assetType(), response.mimeType(), response.fileSize(),
                    response.checksum(), response.versionNumber(), response.lifecycleStatus(), response.processingStatus(),
                    response.createdAt()
            );
        }
        Asset asset = assets.findByIdAndWorkspaceId(assetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
        AssetVersion latestVersion = assetVersions.findByAssetIdAndVersionNumber(asset.getId(), asset.getLatestVersionNumber())
                .orElseThrow(() -> new ResourceNotFoundException("Asset version not found"));
        AssetResponse response = AssetResponse.from(asset, latestVersion);
        AssetMetadataSnapshot snapshot = new AssetMetadataSnapshot(
                response.assetId(), response.assetVersionId(), response.workspaceId(), response.originalFilename(),
                response.displayName(), response.description(), response.assetType(), response.mimeType(), response.fileSize(),
                response.checksum(), response.versionNumber(), response.lifecycleStatus(), response.processingStatus(),
                response.createdAt());
        assetMetadataCache.put(snapshot);
        return snapshot;
    }

    @Override
    @Transactional(readOnly = true)
    public AssetMetadataSnapshot getVersionMetadata(UUID workspaceId, UUID assetId, int versionNumber) {
        Asset asset = requireAsset(workspaceId, assetId);
        AssetVersion version = requireVersion(assetId, versionNumber);
        return snapshot(asset, version);
    }

    @Override
    @Transactional(readOnly = true)
    public AssetMetadataSnapshot getVersionMetadata(UUID workspaceId, UUID assetId, UUID assetVersionId) {
        Asset asset = requireAsset(workspaceId, assetId);
        AssetVersion version = assetVersions.findById(assetVersionId)
                .filter(candidate -> candidate.getAssetId().equals(assetId))
                .orElseThrow(() -> new ResourceNotFoundException("Asset version not found"));
        return snapshot(asset, version);
    }

    private AssetMetadataSnapshot snapshot(Asset asset, AssetVersion version) {
        AssetResponse response = AssetResponse.from(asset, version);
        return new AssetMetadataSnapshot(
                response.assetId(), response.assetVersionId(), response.workspaceId(), response.originalFilename(),
                response.displayName(), response.description(), response.assetType(), response.mimeType(), response.fileSize(),
                response.checksum(), response.versionNumber(), response.lifecycleStatus(), response.processingStatus(),
                response.createdAt()
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetResponse> list(UUID userId, UUID workspaceId, int page, int size) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        validatePage(page, size);
        Page<Asset> assetsPage = assets.findByWorkspaceIdAndDeletedAtIsNull(
                workspaceId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")))
        );
        Map<UUID, AssetVersion> latestVersions = findLatestVersions(assetsPage.getContent());
        List<AssetResponse> content = assetsPage.getContent().stream()
                .map(asset -> AssetResponse.from(asset, latestVersions.get(asset.getId())))
                .toList();
        return new PageResponse<>(content, assetsPage.getNumber(), assetsPage.getSize(),
                assetsPage.getTotalElements(), assetsPage.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<AssetVersionResponse> listVersions(UUID userId, UUID workspaceId, UUID assetId) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        Asset asset = requireAsset(workspaceId, assetId);
        return assetVersions.findByAssetIdOrderByVersionNumberDesc(assetId).stream()
                .map(version -> AssetVersionResponse.from(asset, version))
                .toList();
    }

    @Transactional(readOnly = true)
    public AssetVersionResponse getVersion(UUID userId, UUID workspaceId, UUID assetId, int versionNumber) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        Asset asset = requireAsset(workspaceId, assetId);
        AssetVersion version = requireVersion(assetId, versionNumber);
        return AssetVersionResponse.from(asset, version);
    }

    @Transactional(readOnly = true)
    public VersionDownload downloadVersion(UUID userId, UUID workspaceId, UUID assetId, int versionNumber) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        requireAsset(workspaceId, assetId);
        AssetVersion version = requireVersion(assetId, versionNumber);
        StorageFacade.StoredObjectContent storedObject = storageFacade.open(workspaceId, version.getStorageObjectId());
        return new VersionDownload(
                storedObject.content(), version.getOriginalFilename(), version.getMimeType(), version.getFileSize()
        );
    }

    private Asset requireAsset(UUID workspaceId, UUID assetId) {
        return assets.findByIdAndWorkspaceId(assetId, workspaceId)
                .orElseThrow(() -> new ResourceNotFoundException("Asset not found"));
    }

    private AssetVersion requireVersion(UUID assetId, int versionNumber) {
        return assetVersions.findByAssetIdAndVersionNumber(assetId, versionNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Asset version not found"));
    }

    public record VersionDownload(InputStream content, String filename, String mimeType, long fileSize) {
    }

    private Map<UUID, AssetVersion> findLatestVersions(List<Asset> pageAssets) {
        List<UUID> assetIds = pageAssets.stream().map(Asset::getId).toList();
        return assetIds.isEmpty() ? Map.of() : assetVersions.findByAssetIdIn(assetIds).stream()
                .collect(java.util.stream.Collectors.toMap(
                        AssetVersion::getAssetId,
                        Function.identity(),
                        java.util.function.BinaryOperator.maxBy(Comparator.comparingInt(AssetVersion::getVersionNumber))
                ));
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page and size are outside supported limits");
        }
    }
}
