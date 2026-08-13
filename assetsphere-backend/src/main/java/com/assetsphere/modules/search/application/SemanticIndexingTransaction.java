package com.assetsphere.modules.search.application;

import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticIndexRequest;
import com.assetsphere.modules.search.domain.AssetContentChunk;
import com.assetsphere.modules.search.domain.AssetSemanticIndex;
import com.assetsphere.modules.search.domain.SemanticIndexStatus;
import com.assetsphere.modules.search.persistence.AssetContentChunkRepository;
import com.assetsphere.modules.search.persistence.AssetContentChunkVectorRepository;
import com.assetsphere.modules.search.persistence.AssetSearchDocumentRepository;
import com.assetsphere.modules.search.persistence.AssetSemanticIndexRepository;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class SemanticIndexingTransaction {
    private final AssetSemanticIndexRepository indexes;
    private final AssetSearchDocumentRepository documents;
    private final AssetContentChunkRepository chunks;
    private final AssetContentChunkVectorRepository vectors;
    private final SemanticIndexProperties properties;
    private final ClockProvider clock;

    @Transactional
    SemanticIndexWork claim(SemanticIndexRequest request) {
        AssetSemanticIndex index = indexes.findByAssetVersionId(request.assetVersionId()).orElseGet(() ->
                indexes.save(AssetSemanticIndex.pending(request.workspaceId(), request.assetId(), request.assetVersionId())));
        if (index.isTerminal()) return SemanticIndexWork.skip();
        String text = documents.findExtractedText(request.workspaceId(), request.assetVersionId()).orElse("");
        if (text.isBlank()) {
            index.notApplicable(clock.now());
            return SemanticIndexWork.skip();
        }
        index.start(properties.getModel(), properties.getDimension(), clock.now());
        return new SemanticIndexWork(text);
    }

    @Transactional
    void complete(SemanticIndexRequest request, List<String> texts, List<EmbeddingVector> embeddings) {
        AssetSemanticIndex index = indexes.findByAssetVersionId(request.assetVersionId()).orElseThrow();
        if (index.getStatus() == SemanticIndexStatus.READY) return;
        chunks.deleteByAssetVersionId(request.assetVersionId());
        List<AssetContentChunk> persistedChunks = new ArrayList<>(texts.size());
        for (int i = 0; i < texts.size(); i++) {
            persistedChunks.add(chunks.save(AssetContentChunk.create(
                    request.workspaceId(), request.assetId(), request.assetVersionId(), i, texts.get(i))));
        }
        chunks.flush();
        for (int i = 0; i < persistedChunks.size(); i++) {
            vectors.store(persistedChunks.get(i).getId(), embeddings.get(i).values());
        }
        index.complete(texts.size(), clock.now());
    }

    @Transactional
    void fail(SemanticIndexRequest request, String code) {
        indexes.findByAssetVersionId(request.assetVersionId()).ifPresent(index -> {
            if (!index.isTerminal()) index.fail(code, "Semantic indexing could not be completed", clock.now());
        });
    }

    @Transactional
    void prepareRetry(SemanticIndexRequest request) {
        AssetSemanticIndex index = indexes.findByAssetVersionId(request.assetVersionId())
                .orElseThrow();
        index.prepareRetry();
    }

    record SemanticIndexWork(String text) {
        static SemanticIndexWork skip() {
            return new SemanticIndexWork(null);
        }

        boolean shouldEmbed() {
            return text != null;
        }
    }
}
