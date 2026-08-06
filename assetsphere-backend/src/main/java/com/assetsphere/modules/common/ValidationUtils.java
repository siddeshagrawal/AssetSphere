package com.assetsphere.modules.common;

import java.util.UUID;

public final class ValidationUtils {
    private ValidationUtils() { }
    public static UUID requireId(UUID id, String name) {
        if (id == null) throw new IllegalArgumentException(name + " must be provided");
        return id;
    }
}
