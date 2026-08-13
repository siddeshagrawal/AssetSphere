package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.WorkspacePlanProvider;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.AiModelDescriptor;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiModelCatalogService {
    private final IntelligenceProperties properties;
    private final WorkspacePlanProvider plans;

    public List<AiModelDescriptor> available(UUID workspaceId) {
        Plan plan = plans.currentPlan(workspaceId);
        List<AiModelDescriptor> configured = properties.getModels().stream().map(model -> new AiModelDescriptor(
                model.getProvider().name(), model.getModelId(), model.getDisplayName(), Set.copyOf(model.getCapabilities()),
                model.getMinimumPlan(), model.isEnabled())).toList();
        if (configured.isEmpty()) configured = List.of(new AiModelDescriptor("OPENAI", properties.getModel(),
                "AssetSphere Default", Set.of("ASK", "INTELLIGENCE", "EVOLUTION", "QUIZ"), Plan.FREE, true));
        return configured.stream().filter(model -> model.enabled() && allowed(plan, model.minimumPlan())).toList();
    }

    public AiModelDescriptor select(UUID workspaceId, String modelId, String capability) {
        if (modelId != null && !modelId.isBlank()) return requireAllowed(workspaceId, modelId.trim(), capability);
        Plan plan = plans.currentPlan(workspaceId);
        var configuredDefault = properties.getModels().stream()
                .filter(model -> model.isEnabled() && model.isDefaultModel() && model.getCapabilities().contains(capability)
                        && allowed(plan, model.getMinimumPlan()))
                .max(java.util.Comparator.comparing(model -> model.getMinimumPlan().ordinal()));
        if (configuredDefault.isPresent()) return requireAllowed(workspaceId, configuredDefault.get().getModelId(), capability);
        return available(workspaceId).stream().filter(model -> model.capabilities().contains(capability)).findFirst()
                .orElseThrow(() -> new BusinessRuleViolationException("No AI model is available for this workspace plan"));
    }

    public AiModelDescriptor requireAllowed(UUID workspaceId, String modelId, String capability) {
        return available(workspaceId).stream().filter(model -> model.enabled() && model.modelId().equals(modelId)
                        && model.capabilities().contains(capability))
                .findFirst().orElseThrow(() -> new BusinessRuleViolationException(
                        "The selected AI model is not available for this workspace plan"));
    }

    private boolean allowed(Plan current, Plan minimum) { return current.ordinal() >= minimum.ordinal(); }
}
