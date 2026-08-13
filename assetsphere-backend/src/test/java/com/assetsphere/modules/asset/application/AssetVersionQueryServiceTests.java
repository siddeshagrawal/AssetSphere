package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetMetadataCache;
import com.assetsphere.modules.asset.domain.Asset;
import com.assetsphere.modules.asset.domain.AssetVersion;
import com.assetsphere.modules.asset.persistence.AssetRepository;
import com.assetsphere.modules.asset.persistence.AssetVersionRepository;
import com.assetsphere.modules.storage.api.StorageFacade;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetVersionQueryServiceTests {

    @Test
    void versionHistoryPreservesRepositoryNewestFirstOrdering() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        UUID assetId = UUID.randomUUID();
        WorkspaceAccessFacade workspaceAccess = mock(WorkspaceAccessFacade.class);
        AssetRepository assets = mock(AssetRepository.class);
        AssetVersionRepository versions = mock(AssetVersionRepository.class);
        Asset asset = mock(Asset.class);
        AssetVersion versionThree = version(assetId, 3);
        AssetVersion versionTwo = version(assetId, 2);
        AssetVersion versionOne = version(assetId, 1);
        when(asset.getId()).thenReturn(assetId);
        when(asset.getDisplayName()).thenReturn("Report");
        when(assets.findByIdAndWorkspaceId(assetId, workspaceId)).thenReturn(Optional.of(asset));
        when(versions.findByAssetIdOrderByVersionNumberDesc(assetId))
                .thenReturn(List.of(versionThree, versionTwo, versionOne));
        AssetQueryService service = new AssetQueryService(
                workspaceAccess, assets, versions, mock(AssetMetadataCache.class), mock(StorageFacade.class)
        );

        var history = service.listVersions(userId, workspaceId, assetId);

        assertThat(history).extracting(response -> response.versionNumber()).containsExactly(3, 2, 1);
    }

    private AssetVersion version(UUID assetId, int versionNumber) {
        AssetVersion version = mock(AssetVersion.class);
        when(version.getAssetId()).thenReturn(assetId);
        when(version.getVersionNumber()).thenReturn(versionNumber);
        when(version.getOriginalFilename()).thenReturn("report.pdf");
        when(version.getMimeType()).thenReturn("application/pdf");
        return version;
    }
}
