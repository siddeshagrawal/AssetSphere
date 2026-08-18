package com.assetsphere.modules.asset.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.BillingProperties;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetUploadCapabilityPolicyTests {
    private final UUID workspaceId = UUID.randomUUID();
    private final BillingEntitlementFacade billing = mock(BillingEntitlementFacade.class);
    private final AssetUploadCapabilityPolicy policy = new AssetUploadCapabilityPolicy(billing);
    private final BillingProperties properties = new BillingProperties();

    @Test
    void freeDocumentsRemainAllowed() {
        when(billing.entitlements(workspaceId)).thenReturn(properties.entitlements(Plan.FREE));

        assertThatCode(() -> policy.requireSupported(workspaceId, "application/pdf"))
                .doesNotThrowAnyException();
    }

    @Test
    void freeVideoAndImageRequireTheirExistingProcessingEntitlements() {
        when(billing.entitlements(workspaceId)).thenReturn(properties.entitlements(Plan.FREE));

        assertThatThrownBy(() -> policy.requireSupported(workspaceId, "video/mp4"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Video transcription requires");
        assertThatThrownBy(() -> policy.requireSupported(workspaceId, "image/png"))
                .isInstanceOf(BusinessRuleViolationException.class)
                .hasMessageContaining("Image OCR requires");
    }

    @Test
    void proAndEnterpriseVideoRemainAllowed() {
        when(billing.entitlements(workspaceId)).thenReturn(properties.entitlements(Plan.PRO));
        assertThatCode(() -> policy.requireSupported(workspaceId, "video/webm"))
                .doesNotThrowAnyException();

        when(billing.entitlements(workspaceId)).thenReturn(properties.entitlements(Plan.ENTERPRISE));
        assertThatCode(() -> policy.requireSupported(workspaceId, "video/mp4"))
                .doesNotThrowAnyException();
    }
}
