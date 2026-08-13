package com.assetsphere.infrastructure.payment;

import java.util.Locale;

enum LocalRazorpayVariant {
    MY,
    TUTOR;

    static LocalRazorpayVariant parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Local Razorpay contract variant must be configured as MY or TUTOR");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Unsupported local Razorpay contract variant: " + value, exception);
        }
    }
}
