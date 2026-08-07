package com.assetsphere.modules.workspace.domain;

import java.util.Locale;

public final class SlugNormalizer {

    private SlugNormalizer() {
    }

    public static String normalize(String value) {
        String slug = value == null
                ? ""
                : value.trim()
                        .toLowerCase(Locale.ROOT)
                        .replaceAll("[^a-z0-9]+", "-")
                        .replaceAll("(^-|-$)", "");
        if (slug.length() < 3 || slug.length() > 160) {
            throw new IllegalArgumentException("Slug must be between 3 and 160 characters");
        }
        return slug;
    }
}
