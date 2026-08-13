package com.assetsphere.modules.search.application;

import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.api.SemanticIndexingException;
import com.assetsphere.modules.search.api.SemanticIndexingFacade;
import com.assetsphere.modules.search.api.SemanticIndexingLock;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
class SemanticIndexingApplicationService implements SemanticIndexingFacade {
    private final SemanticIndexingLock lock;
    private final SemanticIndexingTransaction transactions;
    private final DocumentChunker chunker;
    private final ObjectProvider<EmbeddingModelPort> embeddingModel;
    private final SemanticIndexProperties properties;

    @Override
    public void process(SemanticIndexRequest request) {
        try (SemanticIndexingLock.LockHandle handle = lock.tryAcquire(request.assetVersionId())) {
            if (!handle.acquired()) throw SemanticIndexingException.retryable("Semantic indexing is already in progress", null);
            SemanticIndexingTransaction.SemanticIndexWork work = transactions.claim(request);
            if (!work.shouldEmbed()) return;
            List<String> texts = chunker.chunk(work.text());
            List<EmbeddingVector> embeddings = embed(texts);
            transactions.complete(request, texts, embeddings);
        } catch (SemanticIndexingException exception) {
            if (!exception.isRetryable()) {
                log.error("Semantic indexing failed workspaceId={} assetId={} assetVersionId={}",
                        request.workspaceId(), request.assetId(), request.assetVersionId(), exception);
                transactions.fail(request, "INDEXING_FAILED");
            }
            throw exception;
        } catch (RuntimeException exception) {
            log.error("Semantic indexing failed workspaceId={} assetId={} assetVersionId={}",
                    request.workspaceId(), request.assetId(), request.assetVersionId(), exception);
            transactions.fail(request, "INDEXING_FAILED");
            throw SemanticIndexingException.retryable("Semantic indexing failed", exception);
        }
    }

    @Override
    public void markFailed(SemanticIndexRequest request, String failureCode) {
        transactions.fail(request, failureCode);
    }

    @Override
    public void prepareFailedIndexForRetry(SemanticIndexRequest request) {
        transactions.prepareRetry(request);
    }

    private List<EmbeddingVector> embed(List<String> texts) {
        EmbeddingModelPort model = embeddingModel.getIfAvailable();
        if (model == null) throw SemanticIndexingException.terminal("No embedding provider is configured", null);
        List<EmbeddingVector> result = new ArrayList<>();
        for (int offset = 0; offset < texts.size(); offset += properties.getBatchSize()) {
            List<EmbeddingVector> batch = model.embed(texts.subList(offset, Math.min(texts.size(), offset + properties.getBatchSize())));
            result.addAll(batch);
        }
        if (result.size() != texts.size() || result.stream().anyMatch(v -> v.values().length != properties.getDimension()))
            throw SemanticIndexingException.terminal("Embedding dimension does not match configured schema", null);
        return result;
    }

    
}
