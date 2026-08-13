package com.assetsphere.modules.search.api;

public interface SemanticIndexingFacade {

    void process(SemanticIndexRequest request);

    void markFailed(SemanticIndexRequest request, String failureCode);

    void prepareFailedIndexForRetry(SemanticIndexRequest request);
}
