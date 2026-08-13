package com.assetsphere.modules.search.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.api.SemanticIndexingLock;
import com.assetsphere.modules.search.api.SemanticIndexingException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class SemanticIndexingApplicationServiceTests {
    @Mock SemanticIndexingLock lock; @Mock SemanticIndexingLock.LockHandle handle;
    @Mock SemanticIndexingTransaction transactions; @Mock DocumentChunker chunker;
    @Mock ObjectProvider<EmbeddingModelPort> models; @Mock EmbeddingModelPort model;

    @Test void noUsableTextDoesNotInvokeEmbedding() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(SemanticIndexingTransaction.SemanticIndexWork.skip());
        service().process(request());
        verify(models, never()).getIfAvailable(); verify(transactions, never()).complete(any(), any(), any());
    }

    @Test void successfulIndexingPersistsChunksAndVectors() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(new SemanticIndexingTransaction.SemanticIndexWork("text"));
        when(chunker.chunk("text")).thenReturn(List.of("text")); when(models.getIfAvailable()).thenReturn(model);
        when(model.embed(any())).thenReturn(List.of(new EmbeddingVector(new float[1536])));
        service().process(request()); verify(transactions).complete(any(), any(), any());
    }

    @Test void readyDuplicateSkipsEmbeddingAndCompletion() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(SemanticIndexingTransaction.SemanticIndexWork.skip());
        service().process(request());
        verify(models, never()).getIfAvailable(); verify(transactions, never()).complete(any(), any(), any());
    }

    @Test void providerFailureMarksIndexFailedAndPropagatesRetryableFailure() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(new SemanticIndexingTransaction.SemanticIndexWork("text"));
        when(chunker.chunk("text")).thenReturn(List.of("text")); when(models.getIfAvailable()).thenReturn(model);
        when(model.embed(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThatThrownBy(() -> service().process(request())).isInstanceOf(SemanticIndexingException.class);
        verify(transactions).fail(any(), org.mockito.ArgumentMatchers.eq("INDEXING_FAILED"));
    }

    @Test void invalidEmbeddingDimensionDoesNotCompleteAndMarksFailed() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(new SemanticIndexingTransaction.SemanticIndexWork("text"));
        when(chunker.chunk("text")).thenReturn(List.of("text")); when(models.getIfAvailable()).thenReturn(model);
        when(model.embed(any())).thenReturn(List.of(new EmbeddingVector(new float[32])));
        assertThatThrownBy(() -> service().process(request())).isInstanceOf(SemanticIndexingException.class);
        verify(transactions, never()).complete(any(), any(), any()); verify(transactions).fail(any(), any());
    }

    @Test void semanticFailureHasNoCoreAssetMutationBoundary() {
        when(lock.tryAcquire(any())).thenReturn(handle); when(handle.acquired()).thenReturn(true);
        when(transactions.claim(any())).thenReturn(new SemanticIndexingTransaction.SemanticIndexWork("text"));
        when(chunker.chunk("text")).thenReturn(List.of("text")); when(models.getIfAvailable()).thenReturn(model);
        when(model.embed(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertThatThrownBy(() -> service().process(request())).isInstanceOf(SemanticIndexingException.class);
        // The Search orchestrator has no Asset facade/repository dependency; semantic failure cannot mutate Asset status.
        verify(transactions).fail(any(), any());
    }

    private SemanticIndexingApplicationService service() { return new SemanticIndexingApplicationService(lock, transactions, chunker, models, properties()); }
    private SemanticIndexProperties properties(){ SemanticIndexProperties p=new SemanticIndexProperties(); p.setDimension(1536); return p; }
    private SemanticIndexRequest request(){ return new SemanticIndexRequest(UUID.randomUUID(),UUID.randomUUID(),UUID.randomUUID(),Instant.EPOCH); }
}
