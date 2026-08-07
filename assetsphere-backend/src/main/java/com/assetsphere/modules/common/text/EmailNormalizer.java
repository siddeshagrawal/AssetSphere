package com.assetsphere.modules.common.text;

import java.util.Locale;

public final class EmailNormalizer {
    private EmailNormalizer() {
    }

    public static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
