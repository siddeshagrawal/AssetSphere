package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.domain.AssetLifecycleStatus;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import com.assetsphere.modules.intelligence.domain.IntelligenceStatus;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

class IntelligenceGenerationTransactionTests {

    @Test
    void firstRequestCreatesProcessingStateAndPublishesOneEvent() {
        Fixture fixture = fixture(Optional.empty());
        when(fixture.intelligences.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        fixture.transaction.request(fixture.asset);

        ArgumentCaptor<AssetIntelligence> saved = ArgumentCaptor.forClass(AssetIntelligence.class);
        verify(fixture.intelligences).save(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo(IntelligenceStatus.PROCESSING);
        verify(fixture.events).publishEvent(any(AssetReadyForIntelligenceEvent.class));
    }

    @Test
    void repeatedProcessingRequestDoesNotPublishAgain() {
        AssetMetadataSnapshot asset = asset();
        AssetIntelligence processing = AssetIntelligence.requested(
                asset.workspaceId(), asset.assetId(), asset.assetVersionId(), Instant.EPOCH
        );
        Fixture fixture = fixture(asset, Optional.of(processing));

        fixture.transaction.request(asset);

        verify(fixture.events, never()).publishEvent(any(AssetReadyForIntelligenceEvent.class));
    }

    @Test
    void failedRequestIsResetAndRepublishedForControlledRetry() {
        AssetMetadataSnapshot asset = asset();
        AssetIntelligence failed = AssetIntelligence.requested(
                asset.workspaceId(), asset.assetId(), asset.assetVersionId(), Instant.EPOCH
        );
        failed.fail("KAFKA_DLT", "failed", Instant.EPOCH.plusSeconds(1));
        Fixture fixture = fixture(asset, Optional.of(failed));

        fixture.transaction.request(asset);

        assertThat(failed.getStatus()).isEqualTo(IntelligenceStatus.PROCESSING);
        verify(fixture.events).publishEvent(any(AssetReadyForIntelligenceEvent.class));
    }

    private Fixture fixture(Optional<AssetIntelligence> existing) {
        return fixture(asset(), existing);
    }

    private Fixture fixture(AssetMetadataSnapshot asset, Optional<AssetIntelligence> existing) {
        AssetIntelligenceRepository intelligences = mock(AssetIntelligenceRepository.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        AssetIntelligenceResponseMapper mapper = mock(AssetIntelligenceResponseMapper.class);
        when(intelligences.findByAssetVersionId(asset.assetVersionId())).thenReturn(existing);
        IntelligenceGenerationTransaction transaction = new IntelligenceGenerationTransaction(
                intelligences, events, mapper, (ClockProvider) () -> Instant.EPOCH.plusSeconds(2),
                mock(BillingEntitlementFacade.class)
        );
        return new Fixture(asset, intelligences, events, transaction);
    }

    private AssetMetadataSnapshot asset() {
        return new AssetMetadataSnapshot(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "report.pdf", "Report", null,
                AssetType.PDF, "application/pdf", 1, "checksum", 2,
                AssetLifecycleStatus.ACTIVE, AssetProcessingStatus.READY, Instant.EPOCH
        );
    }

    private record Fixture(
            AssetMetadataSnapshot asset,
            AssetIntelligenceRepository intelligences,
            ApplicationEventPublisher events,
            IntelligenceGenerationTransaction transaction
    ) { }
}
