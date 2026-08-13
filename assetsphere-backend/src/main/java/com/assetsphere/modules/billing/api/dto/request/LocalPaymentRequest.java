package com.assetsphere.modules.billing.api.dto.request;

import com.assetsphere.modules.billing.api.LocalPaymentMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LocalPaymentRequest(
        @NotBlank @Size(max = 128) String orderId,
        @NotNull LocalPaymentMethod method,
        @Size(max = 160) String vpa,
        @Size(max = 64) String bank,
        @Size(max = 64) String walletCode,
        @Size(max = 19) String pan,
        @Size(max = 4) String cvv,
        Integer expiryMonth,
        Integer expiryYear,
        @Size(max = 100) String cardHolderName
) {
    @Override
    public String toString() {
        return "LocalPaymentRequest[orderId=" + orderId + ", method=" + method
                + ", cardDetailsPresent=" + (pan != null || cvv != null) + "]";
    }
}
