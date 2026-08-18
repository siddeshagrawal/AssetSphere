package com.assetsphere.modules.billing.api;

import com.assetsphere.modules.billing.api.dto.response.BillingResponse;
import com.assetsphere.modules.billing.application.BillingService;
import com.assetsphere.modules.billing.application.BillingCheckoutService;
import com.assetsphere.modules.billing.api.dto.response.CheckoutResponse;
import com.assetsphere.modules.billing.api.dto.request.LocalPaymentRequest;
import com.assetsphere.modules.billing.api.dto.response.LocalPaymentResponse;
import com.assetsphere.modules.billing.application.LocalPaymentDemoService;
import com.assetsphere.modules.billing.application.StripeSubscriptionReconciliationService;
import com.assetsphere.modules.common.security.CurrentUserProvider;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.common.web.ApiResponse;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.workspace.api.WorkspaceRoleView;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/workspaces/{workspaceId}/billing")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Billing", description = "Workspace plan, usage, and checkout")
class WorkspaceBillingController {
    private final BillingService billing;
    private final BillingCheckoutService checkout;
    private final LocalPaymentDemoService localPayments;
    private final StripeSubscriptionReconciliationService stripeReconciliation;
    private final WorkspaceAccessFacade workspaceAccess;
    private final CurrentUserProvider currentUser;
    private final ClockProvider clock;

    @GetMapping
    @Operation(summary = "Get workspace plan, entitlements, and usage")
    ApiResponse<BillingResponse> get(@PathVariable UUID workspaceId) {
        workspaceAccess.requireActiveMembership(workspaceId, currentUser.requireCurrentUser().id());
        stripeReconciliation.reconcileLegacyIfNeeded(workspaceId);
        return ApiResponse.success(billing.billing(workspaceId), clock);
    }

    @PostMapping("/checkout")
    @Operation(summary = "Create a backend-priced PRO checkout with the configured payment provider")
    ApiResponse<CheckoutResponse> checkout(@PathVariable UUID workspaceId,
                                           @RequestHeader("Idempotency-Key") String idempotencyKey) {
        var user = currentUser.requireCurrentUser();
        workspaceAccess.requireRole(workspaceId, user.id(), Set.of(WorkspaceRoleView.OWNER));
        return ApiResponse.success(checkout.checkout(workspaceId, user.id(), idempotencyKey), clock);
    }

    @PostMapping("/cancel")
    @Operation(summary = "Cancel a recurring Stripe subscription at period end")
    ApiResponse<Void> cancelAtPeriodEnd(@PathVariable UUID workspaceId) {
        var user = currentUser.requireCurrentUser();
        workspaceAccess.requireRole(workspaceId, user.id(), Set.of(WorkspaceRoleView.OWNER));
        billing.cancelAtPeriodEnd(workspaceId);
        return ApiResponse.success(null, clock);
    }

    @PostMapping("/local-payments")
    @Operation(summary = "Initiate a payment against a pending local Razorpay order")
    ApiResponse<LocalPaymentResponse> createLocalPayment(@PathVariable UUID workspaceId,
                                                         @Valid @org.springframework.web.bind.annotation.RequestBody
                                                         LocalPaymentRequest request) {
        var user = currentUser.requireCurrentUser();
        workspaceAccess.requireRole(workspaceId, user.id(), Set.of(WorkspaceRoleView.OWNER));
        return ApiResponse.success(localPayments.create(workspaceId, request), clock);
    }

    @GetMapping("/local-payments/{orderId}/{paymentId}")
    @Operation(summary = "Get normalized local Razorpay payment status")
    ApiResponse<LocalPaymentResponse> getLocalPayment(@PathVariable UUID workspaceId,
                                                      @PathVariable String orderId,
                                                      @PathVariable String paymentId) {
        var user = currentUser.requireCurrentUser();
        workspaceAccess.requireRole(workspaceId, user.id(), Set.of(WorkspaceRoleView.OWNER));
        return ApiResponse.success(localPayments.get(workspaceId, orderId, paymentId), clock);
    }
}
