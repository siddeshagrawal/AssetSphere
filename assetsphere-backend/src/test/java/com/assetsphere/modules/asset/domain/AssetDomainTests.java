package com.assetsphere.modules.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetDomainTests {

    @Test
    void initialUploadStartsAtVersionOneAndUploadedState() {
        Asset asset = Asset.initialUpload(UUID.randomUUID(), UUID.randomUUID(), "Report", null, AssetType.PDF);

        assertThat(asset.getLatestVersionNumber()).isEqualTo(1);
        assertThat(asset.getLifecycleStatus()).isEqualTo(AssetLifecycleStatus.ACTIVE);
        assertThat(asset.getProcessingStatus()).isEqualTo(AssetProcessingStatus.UPLOADED);
    }

    @Test
    void deletionSetsDeletedAtAndLifecycle() {
        Asset asset = Asset.initialUpload(UUID.randomUUID(), UUID.randomUUID(), "Report", null, AssetType.PDF);
        Instant deletedAt = Instant.parse("2026-08-07T00:00:00Z");

        asset.markDeleted(deletedAt);

        assertThat(asset.getLifecycleStatus()).isEqualTo(AssetLifecycleStatus.DELETED);
        assertThat(asset.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void initialVersionUsesExplicitAssetIdAndRejectsInvalidSize() {
        UUID assetId = UUID.randomUUID();
        AssetVersion version = AssetVersion.initial(
                assetId, "report.pdf", "application/pdf", 1, "checksum", UUID.randomUUID(), UUID.randomUUID()
        );

        assertThat(version.getAssetId()).isEqualTo(assetId);
        assertThat(version.getVersionNumber()).isEqualTo(1);
        assertThatThrownBy(() -> AssetVersion.initial(
                assetId, "report.pdf", "application/pdf", 0, "checksum", UUID.randomUUID(), UUID.randomUUID()
        )).isInstanceOf(BusinessRuleViolationException.class);
    }
}
