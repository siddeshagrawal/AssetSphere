package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.intelligence.api.AssetReadyForIntelligenceFacade;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceModel;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProcessingLock;
import com.assetsphere.modules.intelligence.api.IntelligenceProviderException;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class IntelligenceEventProcessor implements AssetReadyForIntelligenceFacade {

    private final IntelligenceProcessingLock lock;
    private final IntelligencePreparationTransaction preparation;
    private final IntelligenceCompletionTransaction completion;
    private final IntelligenceFailureTransaction failure;
    private final ObjectProvider<DocumentIntelligenceModel> model;
    private final DocumentIntelligenceResultSanitizer sanitizer;
    private final MeterRegistry meterRegistry;
    private final BillingEntitlementFacade billing;

    @Override
    public void process(AssetReadyForIntelligenceEvent event) {
        try (IntelligenceProcessingLock.LockHandle handle = lock.tryAcquire(event.assetVersionId())) {
            if (!handle.acquired()) {
                throw new IntelligenceLockUnavailableException();
            }
            IntelligencePreparedWork work = preparation.prepare(event);
            if (!work.shouldInvokeProvider()) {
                meterRegistry.counter("assetsphere.intelligence.skipped").increment();
                return;
            }
            DocumentIntelligenceModel provider = model.getIfAvailable();
            if (provider == null) {
                throw IntelligenceProviderException.nonRetryable("No configured intelligence model provider", null);
            }
            billing.consumeOnce(event.workspaceId(), UsageMetric.AI_INSIGHT, event.eventId());
            long startedAt = System.nanoTime();
            try {
                DocumentIntelligenceResult result = provider.analyze(work.request());
                completion.complete(event, sanitizer.sanitize(result));
                meterRegistry.counter("assetsphere.intelligence.generated").increment();
                meterRegistry.timer("assetsphere.intelligence.duration").record(System.nanoTime() - startedAt, TimeUnit.NANOSECONDS);
                log.info("Intelligence generated eventId={} assetId={} assetVersionId={} workspaceId={} inputCharacters={}",
                        event.eventId(), event.assetId(), event.assetVersionId(), event.workspaceId(), work.request().content().length());
            } catch (IntelligenceProviderException exception) {
                meterRegistry.counter("assetsphere.intelligence.failed").increment();
                throw exception;
            }
        }
    }

    @Override
    public void markFailed(AssetReadyForIntelligenceEvent event, String failureCode) {
        failure.fail(event, failureCode);
        meterRegistry.counter("assetsphere.intelligence.failed").increment();
        log.warn("Intelligence failed eventId={} assetId={} assetVersionId={} workspaceId={} failureCode={}",
                event.eventId(), event.assetId(), event.assetVersionId(), event.workspaceId(), failureCode);
    }
}
