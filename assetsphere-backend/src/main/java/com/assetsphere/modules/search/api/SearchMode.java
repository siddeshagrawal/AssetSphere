package com.assetsphere.modules.search.api;

import com.assetsphere.modules.common.exception.InvalidRequestException;

public enum SearchMode {
    LEXICAL, SEMANTIC, HYBRID;

    public static SearchMode from(String value) {
        try { return value == null || value.isBlank() ? LEXICAL : valueOf(value.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { throw new InvalidRequestException("Search mode must be lexical, semantic, or hybrid"); }
    }
}
