package com.assetsphere.infrastructure.payment;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
@RequiredArgsConstructor
class LocalRazorpayProductionGuard {
    private final LocalRazorpayProperties properties;

    @PostConstruct
    void rejectLocalDemoFeatures() {
        if (properties.isPollConfirmationEnabled()) {
            throw new IllegalStateException("Local Razorpay polling confirmation is forbidden in production");
        }
    }
}
