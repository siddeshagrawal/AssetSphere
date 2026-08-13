package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IntelligenceFailureTransaction {

    private final AssetIntelligenceRepository intelligences;
    private final ClockProvider clock;
    private final AuditService auditService;

    @Transactional
    public void fail(AssetReadyForIntelligenceEvent event, String failureCode) {
        AssetIntelligence intelligence = intelligences.findByAssetVersionId(event.assetVersionId())
                .orElseGet(() -> intelligences.save(AssetIntelligence.pending(
                        event.workspaceId(), event.assetId(), event.assetVersionId()
                )));
        if (intelligence.isTerminal()) {
            return;
        }
        intelligence.fail(failureCode, "Intelligence processing did not complete", clock.now());
        auditService.record(null, AuditAction.INTELLIGENCE_FAILED, event.workspaceId(), "ASSET", event.assetId(),
                "failureCode=" + failureCode);
    }
}
