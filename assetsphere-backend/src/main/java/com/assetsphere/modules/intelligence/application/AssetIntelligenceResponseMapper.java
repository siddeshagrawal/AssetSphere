package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.intelligence.api.dto.response.AssetIntelligenceResponse;
import com.assetsphere.modules.intelligence.domain.AssetIntelligence;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AssetIntelligenceResponseMapper {
    private final ObjectMapper objectMapper;

    AssetIntelligenceResponse from(AssetIntelligence intelligence) {
        return new AssetIntelligenceResponse(
                intelligence.getAssetId(), intelligence.getAssetVersionId(), intelligence.getStatus().name(),
                intelligence.getSummary(), strings(intelligence.getKeyPoints()), strings(intelligence.getTags()),
                intelligence.getProvider() == null ? null : intelligence.getProvider().name(), intelligence.getModel(),
                intelligence.isInputTruncated(), intelligence.getCompletedAt()
        );
    }

    AssetIntelligenceResponse notGenerated(UUID assetId, UUID assetVersionId) {
        return new AssetIntelligenceResponse(
                assetId, assetVersionId, "NOT_GENERATED", null, List.of(), List.of(), null, null, false, null
        );
    }

    private List<String> strings(String serialized) {
        try {
            return objectMapper.readValue(serialized, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted intelligence output is invalid", exception);
        }
    }
}
