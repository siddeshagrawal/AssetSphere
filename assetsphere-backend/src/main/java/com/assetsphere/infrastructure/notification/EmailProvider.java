package com.assetsphere.infrastructure.notification;

import java.util.Locale;

enum EmailProvider {
    SMTP,
    RESEND;

    static EmailProvider parse(String value) {
        try {
            return value == null || value.isBlank() ? SMTP : valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unknown email provider; expected SMTP or RESEND");
        }
    }
}
