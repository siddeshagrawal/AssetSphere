package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.audit.api.AuditAction;
import com.assetsphere.modules.audit.api.AuditService;
import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.common.time.ClockProvider;
import com.assetsphere.modules.processing.api.AssetReadyForIntelligenceEvent;
import com.assetsphere.modules.intelligence.persistence.AssetIntelligenceRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
class IntelligenceCompletionTransaction {

    private final AssetIntelligenceRepository intelligences;
    private final ObjectMapper objectMapper;
    private final ClockProvider clock;
    private final AuditService auditService;

    @Transactional
    public void complete(AssetReadyForIntelligenceEvent event, SanitizedIntelligenceResult result) {
        var intelligence = intelligences.findByAssetVersionId(event.assetVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("Asset intelligence not found"));
        if (intelligence.isTerminal()) {
            return;
        }
        intelligence.complete(result.summary(), write(result.keyPoints()), write(result.tags()), clock.now());
        auditService.record(null, AuditAction.INTELLIGENCE_GENERATED, event.workspaceId(), "ASSET", event.assetId(),
                "provider=" + intelligence.getProvider() + ";model=" + intelligence.getModel()
                        + ";inputCharacters=" + intelligence.getInputCharacters());
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize validated intelligence output", exception);
        }
    }
}
