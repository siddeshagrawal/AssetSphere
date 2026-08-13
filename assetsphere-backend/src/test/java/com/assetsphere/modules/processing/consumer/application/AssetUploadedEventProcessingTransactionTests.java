package com.assetsphere.modules.processing.consumer.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetProcessingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.asset.domain.AssetProcessingStatus;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.content.persistence.AssetTextContentRepository;
import com.assetsphere.modules.processing.consumer.persistence.ProcessedEventRepository;
import com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent;
import com.assetsphere.modules.processing.text.ExtractionResult;
import com.assetsphere.modules.processing.text.TextExtractionService;
import com.assetsphere.modules.search.api.SearchIndexCommand;
import com.assetsphere.modules.search.api.SearchIndexFacade;
import com.assetsphere.modules.storage.api.StorageFacade;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class AssetUploadedEventProcessingTransactionTests {
    @Mock ProcessedEventRepository processedEvents; @Mock AssetProcessingFacade assets; @Mock StorageFacade storage;
    @Mock TextExtractionService extraction; @Mock AssetTextContentRepository textContents;
    @Mock SearchIndexFacade searchIndex; @Mock ApplicationEventPublisher eventPublisher;

    @Test void completesContentIndexAndStateOnceForNewEvent() {
        AssetUploadedEvent event = event(); AssetProcessingInput input = input(event);
        when(processedEvents.reserve(any(), any(), any())).thenReturn(1); when(assets.beginProcessing(any(), any(), any())).thenReturn(input);
        when(textContents.findByAssetVersionId(input.assetVersionId())).thenReturn(Optional.empty());
        assertThat(transaction().claim(event)).isEqualTo(input);
        assertThat(transaction().complete(event, input, ExtractionResult.extracted("text", "TEST"))).isTrue();
        verify(assets).completeProcessing(event.assetId(), event.assetVersionId()); verify(textContents).save(any());
        verify(searchIndex).index(any());
        verify(eventPublisher).publishEvent(any(AssetReadyForSemanticIndexEvent.class));
        verify(eventPublisher, never()).publishEvent(any(com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent.class));
    }

    @Test void duplicateEventSkipsExtractionPersistenceIndexAndState() {
        when(processedEvents.existsById(any())).thenReturn(true);
        assertThat(transaction().claim(event())).isNull();
        verify(assets, never()).queueUploadedAsset(any(), any()); verify(storage, never()).open(any());
    }

    @Test void claimDoesNotPersistContentOrCompleteStateBeforeExtraction() {
        AssetUploadedEvent event = event();
        when(assets.beginProcessing(any(), any(), any())).thenReturn(input(event));
        assertThat(transaction().claim(event)).isNotNull();
        verify(assets, never()).completeProcessing(any(), any()); verify(textContents, never()).save(any());
        verify(searchIndex, never()).index(any());
    }

    private AssetUploadedEventProcessingTransaction transaction() { return new AssetUploadedEventProcessingTransaction(processedEvents, assets, (ClockProvider) () -> Instant.EPOCH, textContents, searchIndex, eventPublisher); }
    private AssetUploadedEvent event() { UUID asset=UUID.randomUUID(), version=UUID.randomUUID(); return new AssetUploadedEvent(UUID.randomUUID(),1,Instant.EPOCH,asset,version,UUID.randomUUID(),UUID.randomUUID(),"file.pdf","application/pdf",1,"a".repeat(64),"object",AssetProcessingStatus.UPLOADED); }
    private AssetProcessingInput input(AssetUploadedEvent event) { return new AssetProcessingInput(event.workspaceId(),event.assetId(),event.assetVersionId(),event.storageObjectKey(),"Name",null,event.filename(),event.mimeType(),event.size()); }
}
