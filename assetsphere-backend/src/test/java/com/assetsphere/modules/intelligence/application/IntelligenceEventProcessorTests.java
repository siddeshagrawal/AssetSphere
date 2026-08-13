package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.intelligence.api.DocumentIntelligenceModel;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceRequest;
import com.assetsphere.modules.intelligence.api.DocumentIntelligenceResult;
import com.assetsphere.modules.intelligence.api.IntelligenceProcessingLock;
import com.assetsphere.modules.intelligence.api.IntelligenceProviderException;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;

class IntelligenceEventProcessorTests {

    @Test
    void invokesProviderOnceAndCompletesPreparedWork() {
        Fixture fixture = fixture(true);
        DocumentIntelligenceModel model = mock(DocumentIntelligenceModel.class);
        when(fixture.models.getIfAvailable()).thenReturn(model);
        when(model.analyze(any())).thenReturn(new DocumentIntelligenceResult("Summary", List.of("Point"), List.of("tag")));
        when(fixture.sanitizer.sanitize(any())).thenReturn(new SanitizedIntelligenceResult("Summary", List.of("Point"), List.of("tag")));

        fixture.processor.process(fixture.event);

        verify(model).analyze(fixture.work.request());
        verify(fixture.completion).complete(fixture.event, new SanitizedIntelligenceResult("Summary", List.of("Point"), List.of("tag")));
        verify(fixture.lockHandle).close();
        verify(fixture.billing).consumeOnce(fixture.event.workspaceId(), UsageMetric.AI_INSIGHT, fixture.event.eventId());
    }

    @Test
    void skipsTerminalOrDisabledWorkWithoutAProviderCall() {
        Fixture fixture = fixture(false);

        fixture.processor.process(fixture.event);

        verify(fixture.models, never()).getIfAvailable();
        verify(fixture.completion, never()).complete(any(), any());
        verify(fixture.billing, never()).consumeOnce(any(), any(), any());
    }

    @Test
    void propagatesTransientProviderFailureWithoutFalseCompletion() {
        Fixture fixture = fixture(true);
        DocumentIntelligenceModel model = mock(DocumentIntelligenceModel.class);
        when(fixture.models.getIfAvailable()).thenReturn(model);
        when(model.analyze(any())).thenThrow(IntelligenceProviderException.retryable("temporary", null));

        assertThatThrownBy(() -> fixture.processor.process(fixture.event))
                .isInstanceOf(IntelligenceProviderException.class)
                .satisfies(exception -> org.assertj.core.api.Assertions.assertThat(
                        ((IntelligenceProviderException) exception).isRetryable()).isTrue());
        verify(fixture.completion, never()).complete(any(), any());
    }

    @Test
    void marksIntelligenceFailedFromDltWithoutAccessingAssetState() {
        Fixture fixture = fixture(false);

        fixture.processor.markFailed(fixture.event, "KAFKA_DLT");

        verify(fixture.failure).fail(fixture.event, "KAFKA_DLT");
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture(boolean shouldInvokeProvider) {
        IntelligenceProcessingLock lock = mock(IntelligenceProcessingLock.class);
        IntelligenceProcessingLock.LockHandle handle = mock(IntelligenceProcessingLock.LockHandle.class);
        when(lock.tryAcquire(any())).thenReturn(handle);
        when(handle.acquired()).thenReturn(true);
        IntelligencePreparationTransaction preparation = mock(IntelligencePreparationTransaction.class);
        AssetReadyForIntelligenceEvent event = AssetReadyForIntelligenceEvent.create(
                UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH
        );
        IntelligencePreparedWork work = shouldInvokeProvider
                ? new IntelligencePreparedWork(new DocumentIntelligenceRequest(
                event.assetId(), event.assetVersionId(), event.workspaceId(), "gpt-4o-mini", "memo.pdf", "application/pdf", "Document text", false
        ))
                : IntelligencePreparedWork.skipped();
        when(preparation.prepare(event)).thenReturn(work);
        IntelligenceCompletionTransaction completion = mock(IntelligenceCompletionTransaction.class);
        IntelligenceFailureTransaction failure = mock(IntelligenceFailureTransaction.class);
        ObjectProvider<DocumentIntelligenceModel> models = mock(ObjectProvider.class);
        DocumentIntelligenceResultSanitizer sanitizer = mock(DocumentIntelligenceResultSanitizer.class);
        BillingEntitlementFacade billing = mock(BillingEntitlementFacade.class);
        return new Fixture(event, work, handle, completion, failure, models, sanitizer, billing,
                new IntelligenceEventProcessor(lock, preparation, completion, failure, models, sanitizer,
                        new SimpleMeterRegistry(), billing));
    }

    private record Fixture(
            AssetReadyForIntelligenceEvent event,
            IntelligencePreparedWork work,
            IntelligenceProcessingLock.LockHandle lockHandle,
            IntelligenceCompletionTransaction completion,
            IntelligenceFailureTransaction failure,
            ObjectProvider<DocumentIntelligenceModel> models,
            DocumentIntelligenceResultSanitizer sanitizer,
            BillingEntitlementFacade billing,
            IntelligenceEventProcessor processor
    ) {
    }
}
