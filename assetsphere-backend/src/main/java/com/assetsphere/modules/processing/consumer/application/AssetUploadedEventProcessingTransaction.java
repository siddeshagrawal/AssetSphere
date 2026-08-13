package com.assetsphere.modules.processing.consumer.application;

import com.assetsphere.modules.asset.api.AssetProcessingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.content.domain.AssetTextContent;
import com.assetsphere.modules.processing.content.persistence.AssetTextContentRepository;
import com.assetsphere.modules.processing.consumer.persistence.ProcessedEventRepository;
import com.assetsphere.modules.processing.text.ExtractionResult;
import com.assetsphere.modules.search.api.SearchIndexCommand;
import com.assetsphere.modules.search.api.SearchIndexFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class AssetUploadedEventProcessingTransaction {

    static final String CONSUMER_NAME = "asset-uploaded-state";

    private final ProcessedEventRepository processedEvents;
    private final AssetProcessingFacade assetProcessingFacade;
    private final ClockProvider clockProvider;
    private final AssetTextContentRepository textContents;
    private final SearchIndexFacade searchIndexFacade;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    AssetProcessingInput claim(AssetUploadedEvent event) {
        if (processedEvents.existsById(event.eventId())) return null;
        assetProcessingFacade.prepareProcessingAttemptRetry(event.assetId(), event.assetVersionId());
        assetProcessingFacade.queueUploadedAsset(event.assetId(), event.assetVersionId());
        return assetProcessingFacade.beginProcessing(
                event.assetId(), event.assetVersionId(), event.storageObjectKey()
        );
    }

    @Transactional
    boolean complete(AssetUploadedEvent event, AssetProcessingInput input, ExtractionResult extraction) {
        if (processedEvents.reserve(event.eventId(), CONSUMER_NAME, clockProvider.now()) == 0) return false;
        AssetTextContent textContent = textContents.findByAssetVersionId(input.assetVersionId())
                .map(existing -> {
                    existing.replace(extraction);
                    return existing;
                })
                .orElseGet(() -> AssetTextContent.create(
                        input.workspaceId(), input.assetId(), input.assetVersionId(), extraction
        ));
        textContents.save(textContent);
        searchIndexFacade.index(new SearchIndexCommand(
                input.workspaceId(), input.assetId(), input.assetVersionId(), input.displayName(),
                input.originalFilename(), input.description(), input.mimeType(), "READY", textContent.getExtractedText()
        ));
        assetProcessingFacade.completeProcessing(event.assetId(), event.assetVersionId());
        eventPublisher.publishEvent(com.assetsphere.modules.processing.api.AssetReadyForSemanticIndexEvent.create(
                input.workspaceId(), input.assetId(), input.assetVersionId(), clockProvider.now()
        ));
        return true;
    }

    @Transactional
    void prepareAttemptRetry(AssetUploadedEvent event) {
        assetProcessingFacade.prepareProcessingAttemptRetry(event.assetId(), event.assetVersionId());
    }

    @Transactional
    public void markFailed(AssetUploadedEvent event) {
        assetProcessingFacade.failProcessing(event.assetId(), event.assetVersionId());
    }

}
