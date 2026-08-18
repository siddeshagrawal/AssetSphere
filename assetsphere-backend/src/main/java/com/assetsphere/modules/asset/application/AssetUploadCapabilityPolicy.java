package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssetUploadCapabilityPolicy {
    private static final Set<String> TRANSCRIBED_VIDEO_TYPES = Set.of("video/mp4", "video/webm");

    private final BillingEntitlementFacade billing;

    void requireSupported(UUID workspaceId, String mimeType) {
        if (TRANSCRIBED_VIDEO_TYPES.contains(mimeType)) {
            if (!billing.entitlements(workspaceId).videoTranscriptionEnabled()) {
                throw new BusinessRuleViolationException(
                        "Video transcription requires a PRO or ENTERPRISE workspace plan");
            }
            return;
        }
        if (mimeType != null && mimeType.startsWith("image/")) {
            if (!billing.entitlements(workspaceId).ocrEnabled()) {
                throw new BusinessRuleViolationException(
                        "Image OCR requires a PRO or ENTERPRISE workspace plan");
            }
        }
    }
}
