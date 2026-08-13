package com.assetsphere.modules.search.application;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.domain.AssetContentChunk;
import com.assetsphere.modules.search.domain.AssetSemanticIndex;
import com.assetsphere.modules.search.persistence.AssetContentChunkRepository;
import com.assetsphere.modules.search.persistence.AssetContentChunkVectorRepository;
import com.assetsphere.modules.search.persistence.AssetSearchDocumentRepository;
import com.assetsphere.modules.search.persistence.AssetSemanticIndexRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SemanticIndexingTransactionTests {
    @Mock
    AssetSemanticIndexRepository indexes;
    @Mock
    AssetSearchDocumentRepository documents;
    @Mock
    AssetContentChunkRepository chunks;
    @Mock
    AssetContentChunkVectorRepository vectors;

    @Test
    void completionReplacesExistingChunksBeforePersistingOnePerOrdinal() {
        SemanticIndexRequest request = request();
        AssetSemanticIndex index = AssetSemanticIndex.pending(request.workspaceId(), request.assetId(), request.assetVersionId());
        index.start("text-embedding-3-small", 1536, Instant.EPOCH);
        when(indexes.findByAssetVersionId(request.assetVersionId())).thenReturn(Optional.of(index));
        when(chunks.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        transaction().complete(request, List.of("one", "two"), List.of(new EmbeddingVector(new float[1536]), new EmbeddingVector(new float[1536])));
        verify(chunks).deleteByAssetVersionId(request.assetVersionId());
        verify(chunks, times(2)).save(any());
        verify(chunks).flush();
        verify(vectors, times(2)).store(any(), any());
    }

    private SemanticIndexingTransaction transaction() {
        SemanticIndexProperties properties = new SemanticIndexProperties();
        return new SemanticIndexingTransaction(indexes, documents, chunks, vectors, properties, (ClockProvider) () -> Instant.EPOCH);
    }

    private SemanticIndexRequest request() {
        return new SemanticIndexRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), Instant.EPOCH);
    }
}
