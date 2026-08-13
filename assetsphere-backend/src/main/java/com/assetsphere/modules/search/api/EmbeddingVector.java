package com.assetsphere.modules.search.api;

import java.util.Arrays;

public record EmbeddingVector(float[] values) {

    public EmbeddingVector {
        if (values == null || values.length == 0) {
            throw new IllegalArgumentException("Embedding values are required");
        }
        values = Arrays.copyOf(values, values.length);
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }
}
