package com.assetsphere.modules.billing.api;

import com.assetsphere.modules.billing.application.BillingWebhookService;
import lombok.RequiredArgsConstructor;
import io.swagger.v3.oas.annotations.Hidden;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/billing/webhooks")
@Hidden
class BillingWebhookController {
    private final BillingWebhookService webhooks;

    @PostMapping("/razorpay-local")
    ResponseEntity<Void> localRazorpay(@RequestHeader("X-Razorpay-Signature") String signature,
                                       @RequestBody String payload) {
        webhooks.handle(PaymentProvider.RAZORPAY_LOCAL, null, payload, signature);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/stripe")
    ResponseEntity<Void> stripe(@RequestHeader("Stripe-Signature") String signature,
                                @RequestBody String payload) {
        webhooks.handle(PaymentProvider.STRIPE, null, payload, signature);
        return ResponseEntity.ok().build();
    }
}
