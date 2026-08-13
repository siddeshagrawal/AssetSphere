package com.assetsphere.modules.billing.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.billing.api.PaymentProvider;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.domain.BillingPayment;
import com.assetsphere.modules.billing.domain.Subscription;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;

class BillingProviderIdentityTests {
    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "NULL", " NuLl "})
    void billingPaymentRejectsMissingProviderIdentity(String identity) {
        BillingPayment payment = payment(PaymentProvider.RAZORPAY_LOCAL);

        assertThatThrownBy(() -> payment.paid(identity, NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> payment.providerPaymentCreated(identity)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void billingPaymentPreservesValidIdentityAndLocalRazorpayIdsRemainValid() {
        BillingPayment payment = payment(PaymentProvider.RAZORPAY_LOCAL);
        payment.providerPaymentCreated("pay_local_123");
        payment.paid("pay_local_123", NOW);

        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_local_123");
        assertThatThrownBy(() -> payment.paid("pay_local_456", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThat(payment.getProviderPaymentId()).isEqualTo("pay_local_123");
    }

    @Test
    void stripeBillingPaymentRejectsNonSubscriptionIdentity() {
        BillingPayment payment = payment(PaymentProvider.STRIPE);

        assertThatThrownBy(() -> payment.paid("pi_123", NOW))
                .isInstanceOf(IllegalArgumentException.class);
        payment.paid("sub_123", NOW);
        assertThat(payment.getProviderPaymentId()).isEqualTo("sub_123");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "null", "NULL", " NuLl "})
    void subscriptionRejectsMissingExternalIdentityAndNeverMatchesIt(String identity) {
        Subscription subscription = Subscription.free(UUID.randomUUID(), NOW, NOW.plusSeconds(3600));

        assertThatThrownBy(() -> subscription.activatePro(
                PaymentProvider.RAZORPAY_LOCAL.name(), identity, NOW, NOW.plusSeconds(3600)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(subscription.matches(PaymentProvider.RAZORPAY_LOCAL.name(), identity)).isFalse();
    }

    @Test
    void validStripeIdentityWorksAndCannotBeReplaced() {
        Subscription subscription = Subscription.free(UUID.randomUUID(), NOW, NOW.plusSeconds(3600));
        subscription.activatePro(PaymentProvider.STRIPE.name(), "sub_A", NOW, NOW.plusSeconds(3600));

        assertThat(subscription.matches(PaymentProvider.STRIPE.name(), "sub_A")).isTrue();
        assertThat(subscription.matches(PaymentProvider.STRIPE.name(), "null")).isFalse();
        assertThatThrownBy(() -> subscription.activatePro(
                PaymentProvider.STRIPE.name(), "sub_B", NOW, NOW.plusSeconds(7200)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("sub_A");
    }

    @Test
    void hydratedLiteralNullIdentitiesAreMissingAndRecoverable() {
        BillingPayment payment = payment(PaymentProvider.STRIPE);
        payment.paid("sub_original", NOW);
        ReflectionTestUtils.setField(payment, "providerPaymentId", "NULL");
        assertThat(payment.hasProviderPaymentIdentity()).isFalse();
        payment.recoverPaidProviderIdentity("sub_123");
        assertThat(payment.getProviderPaymentId()).isEqualTo("sub_123");

        Subscription subscription = Subscription.free(UUID.randomUUID(), NOW, NOW.plusSeconds(3600));
        subscription.activatePro(PaymentProvider.STRIPE.name(), "sub_original", NOW, NOW.plusSeconds(3600));
        ReflectionTestUtils.setField(subscription, "externalSubscriptionId", "null");
        assertThat(subscription.hasExternalSubscriptionIdentity()).isFalse();
        assertThat(subscription.matches(PaymentProvider.STRIPE.name(), "null")).isFalse();
        subscription.recoverMissingStripeSubscriptionId("sub_123");
        assertThat(subscription.getExternalSubscriptionId()).isEqualTo("sub_123");
    }

    private BillingPayment payment(PaymentProvider provider) {
        BillingPayment payment = BillingPayment.create(UUID.randomUUID(), UUID.randomUUID(), Plan.PRO,
                provider, "key", "receipt", 99_900, "INR");
        payment.orderCreated("order_123", null);
        return payment;
    }
}
