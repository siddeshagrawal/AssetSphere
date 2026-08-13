package com.assetsphere.modules.asset.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetProcessingLifecycleTests {

    @Test
    void transitionsUploadedAssetThroughQueuedProcessingAndReady() {
        Asset asset = Asset.initialUpload(UUID.randomUUID(), UUID.randomUUID(), "Asset", null, AssetType.PDF);

        asset.queueForProcessing();
        asset.beginProcessing();
        asset.completeProcessing();

        assertThat(asset.getProcessingStatus()).isEqualTo(AssetProcessingStatus.READY);
    }

    @Test
    void rejectsCompletionBeforeProcessing() {
        Asset asset = Asset.initialUpload(UUID.randomUUID(), UUID.randomUUID(), "Asset", null, AssetType.PDF);

        assertThatThrownBy(asset::completeProcessing).isInstanceOf(BusinessRuleViolationException.class);
    }

    @Test
    void marksQueuedAssetAsFailedOnlyAtTerminalHandling() {
        Asset asset = Asset.initialUpload(UUID.randomUUID(), UUID.randomUUID(), "Asset", null, AssetType.PDF);
        asset.queueForProcessing();

        asset.failProcessing();

        assertThat(asset.getProcessingStatus()).isEqualTo(AssetProcessingStatus.FAILED);
    }
}
