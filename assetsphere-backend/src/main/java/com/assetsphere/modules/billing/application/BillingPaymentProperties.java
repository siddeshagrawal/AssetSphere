package com.assetsphere.modules.billing.application;

import java.time.Duration;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "assetsphere.billing.payment")
public class BillingPaymentProperties {
    private long proPriceMinor = 99_900;
    private String currency = "INR";
    private Duration attemptFeedbackWindow = Duration.ofMinutes(15);
    private Duration pendingCheckoutWindow = Duration.ofHours(24);
}
