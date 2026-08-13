package com.assetsphere.modules.search.api;

import java.util.List;

/** Provider-neutral batch embedding boundary. Infrastructure owns Spring AI provider types. */
public interface EmbeddingModelPort {

    List<EmbeddingVector> embed(List<String> inputs);
}
