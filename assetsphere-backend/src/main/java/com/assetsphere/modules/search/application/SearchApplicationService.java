package com.assetsphere.modules.search.application;

import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.ServiceUnavailableException;
import com.assetsphere.modules.common.web.PageResponse;
import com.assetsphere.modules.search.api.AssetSearchResult;
import com.assetsphere.modules.search.api.EmbeddingModelPort;
import com.assetsphere.modules.search.api.EmbeddingVector;
import com.assetsphere.modules.search.api.SearchMode;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import com.assetsphere.modules.search.api.SemanticSearchRateLimiter;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.search.persistence.AssetSearchDocumentRepository;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.springframework.beans.factory.ObjectProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SearchApplicationService implements WorkspaceSearchEvidenceRetriever {

    private static final int MAX_QUERY_LENGTH = 200;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_EVIDENCE_LIMIT = 20;
    private static final int MAX_EVIDENCE_CHARACTERS = 2_500;

    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetSearchDocumentRepository documents;
    private final ObjectProvider<EmbeddingModelPort> embeddingModel;
    private final SemanticIndexProperties semanticProperties;
    private final SemanticSearchRateLimiter rateLimiter;

    @Transactional(readOnly = true)
    public PageResponse<AssetSearchResult> search(UUID userId, UUID workspaceId, String query, int page, int size) {
        return search(userId, workspaceId, query, page, size, SearchMode.LEXICAL);
    }

    @Transactional(readOnly = true)
    public PageResponse<AssetSearchResult> search(UUID userId, UUID workspaceId, String query, int page, int size, SearchMode mode) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        String normalizedQuery = validateQuery(query);
        validatePage(page, size);
        if (mode == SearchMode.SEMANTIC) {
            rateLimiter.check(workspaceId, userId);
            List<AssetSearchResult> results = documents.semanticSearch(workspaceId, embed(normalizedQuery), Math.min(size, semanticProperties.getSemanticSearchLimit()));
            return new PageResponse<>(results, page, size, results.size(), results.isEmpty() ? 0 : 1);
        }
        if (mode == SearchMode.HYBRID) {
            rateLimiter.check(workspaceId, userId);
            return page(hybridSearch(workspaceId, normalizedQuery, size), page, size);
        }
        long total = documents.count(workspaceId, normalizedQuery);
        return new PageResponse<>(documents.search(workspaceId, normalizedQuery, size, (long) page * size),
                page, size, total, (int) Math.ceil((double) total / size));
    }

    @Override
    @Transactional(readOnly = true)
    public List<WorkspaceSearchEvidence> retrieve(UUID workspaceId, String query, int limit) {
        String normalizedQuery = validateQuery(query);
        if (limit < 1 || limit > MAX_EVIDENCE_LIMIT) {
            throw new InvalidRequestException("Evidence limit is outside supported limits");
        }
        return hybridSearch(workspaceId, normalizedQuery, limit).stream()
                .map(this::toEvidence)
                .filter(evidence -> !evidence.text().isBlank())
                .toList();
    }

    private List<AssetSearchResult> hybridSearch(UUID workspaceId, String query, int limit) {
        List<AssetSearchResult> lexical = documents.search(workspaceId, query, limit, 0);
        try {
            return hybrid(lexical, documents.semanticSearch(
                    workspaceId, embed(query), Math.min(limit, semanticProperties.getSemanticSearchLimit())))
                    .stream().limit(limit).toList();
        } catch (ServiceUnavailableException exception) {
            return lexical.stream().limit(limit).toList();
        }
    }

    private WorkspaceSearchEvidence toEvidence(AssetSearchResult result) {
        String title = result.displayName() == null || result.displayName().isBlank()
                ? result.originalFilename() : result.displayName();
        String snippet = result.snippet() == null ? "" : result.snippet().trim();
        if (snippet.length() > MAX_EVIDENCE_CHARACTERS) {
            snippet = snippet.substring(0, MAX_EVIDENCE_CHARACTERS);
        }
        return new WorkspaceSearchEvidence(result.assetId(), result.assetVersionId(), title,
                result.originalFilename(), null, snippet);
    }

    private PageResponse<AssetSearchResult> page(List<AssetSearchResult> values, int page, int size) { return new PageResponse<>(values, page, size, values.size(), values.isEmpty()?0:1); }
    private List<AssetSearchResult> hybrid(List<AssetSearchResult> lexical, List<AssetSearchResult> semantic) {
        Map<UUID, Hybrid> merged = new LinkedHashMap<>(); merge(merged, lexical); merge(merged, semantic);
        return merged.values().stream().sorted(java.util.Comparator.comparingDouble(Hybrid::score).reversed().thenComparing(h -> h.result().assetId())).map(Hybrid::result).toList();
    }
    private void merge(Map<UUID, Hybrid> merged, List<AssetSearchResult> values) { for(int i=0;i<values.size();i++){ AssetSearchResult candidate=values.get(i); double contribution=1d/(60+i+1); Hybrid existing=merged.get(candidate.assetId()); merged.put(candidate.assetId(), existing==null?new Hybrid(candidate,contribution):new Hybrid(existing.score()>=contribution?existing.result():candidate,existing.score()+contribution)); } }
    private record Hybrid(AssetSearchResult result,double score) {}

    private float[] embed(String query) {
        EmbeddingModelPort model = embeddingModel.getIfAvailable();
        if (model == null) throw new ServiceUnavailableException("Semantic search is unavailable", null);
        try {
            List<EmbeddingVector> vectors = model.embed(List.of(query));
            if (vectors.size() != 1 || vectors.getFirst().values().length != semanticProperties.getDimension()) throw new ServiceUnavailableException("Semantic search embedding is invalid", null);
            return vectors.getFirst().values();
        } catch (ServiceUnavailableException exception) { throw exception;
        } catch (RuntimeException exception) { throw new ServiceUnavailableException("Semantic search is unavailable", exception); }
    }

    private String validateQuery(String query) {
        if (query == null || query.isBlank() || query.trim().length() > MAX_QUERY_LENGTH) {
            throw new InvalidRequestException("Search query must contain 1 to 200 characters");
        }
        return query.trim();
    }

    private void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidRequestException("Page and size are outside supported limits");
        }
    }
}
