package com.assetsphere.modules.search.api;

/**
 * Narrow search contract used by Processing to upsert lexical index documents synchronously.
 */
public interface SearchIndexFacade {

    void index(SearchIndexCommand command);
}
