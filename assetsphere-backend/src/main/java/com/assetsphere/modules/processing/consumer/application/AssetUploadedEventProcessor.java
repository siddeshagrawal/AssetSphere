package com.assetsphere.modules.processing.consumer.application;

import com.assetsphere.modules.asset.api.AssetUploadedEvent;
import com.assetsphere.modules.processing.api.AssetProcessingLock;
import com.assetsphere.modules.processing.api.AssetEventProcessingFacade;
import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.assetsphere.modules.processing.text.ExtractionResult;
import com.assetsphere.modules.processing.text.TextExtractionService;
import com.assetsphere.modules.storage.api.StorageFacade;
import java.io.IOException;
import java.io.InputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AssetUploadedEventProcessor implements AssetEventProcessingFacade {

    private final AssetProcessingLock processingLock;
    private final AssetUploadedEventProcessingTransaction transaction;
    private final StorageFacade storageFacade;
    private final TextExtractionService textExtractionService;

    @Override
    public void process(AssetUploadedEvent event) {
        try (AssetProcessingLock.LockHandle lock = processingLock.tryAcquire(event.assetVersionId())) {
            if (!lock.acquired()) {
                throw new ProcessingLockUnavailableException();
            }
            AssetProcessingInput input = transaction.claim(event);
            if (input == null) return;
            try {
                ExtractionResult extraction = extract(input);
                transaction.complete(event, input, extraction);
            } catch (RuntimeException exception) {
                try {
                    transaction.prepareAttemptRetry(event);
                } catch (RuntimeException stateException) {
                    exception.addSuppressed(stateException);
                }
                throw exception;
            }
        }
    }

    @Override
    public void markFailed(AssetUploadedEvent event) {
        transaction.markFailed(event);
    }

    private ExtractionResult extract(AssetProcessingInput input) {
        try (InputStream content = storageFacade.open(input.storageObjectKey())) {
            return textExtractionService.extract(input, content);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to close storage content stream", exception);
        }
    }
}
