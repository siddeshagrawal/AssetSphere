package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.WorkspacePlanProvider;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import java.util.UUID;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AiModelCatalogServiceTests {
    @Test
    void resolvesModelsFromTheRequestedWorkspacePlan() {
        UUID workspaceId = UUID.randomUUID();
        WorkspacePlanProvider plans = mock(WorkspacePlanProvider.class);
        when(plans.currentPlan(workspaceId)).thenReturn(Plan.FREE);
        IntelligenceProperties properties = new IntelligenceProperties();
        properties.setModel("gpt-4o-mini");
        var models = new AiModelCatalogService(properties, plans).available(workspaceId);
        assertThat(models).singleElement().satisfies(model -> {
            assertThat(model.modelId()).isEqualTo("gpt-4o-mini");
            assertThat(model.minimumPlan()).isEqualTo(Plan.FREE);
        });
    }

    @Test
    void validatesConfiguredModelPlanAndCapability() {
        UUID workspaceId = UUID.randomUUID();
        WorkspacePlanProvider plans = mock(WorkspacePlanProvider.class);
        when(plans.currentPlan(workspaceId)).thenReturn(Plan.FREE);
        IntelligenceProperties properties = new IntelligenceProperties();
        IntelligenceProperties.Model advanced = new IntelligenceProperties.Model();
        advanced.setModelId("gpt-advanced");
        advanced.setDisplayName("Advanced");
        advanced.setMinimumPlan(Plan.PRO);
        advanced.setCapabilities(Set.of("QUIZ"));
        properties.setModels(List.of(advanced));
        AiModelCatalogService catalog = new AiModelCatalogService(properties, plans);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> catalog.requireAllowed(workspaceId, "gpt-advanced", "QUIZ"))
                .isInstanceOf(com.assetsphere.modules.common.exception.BusinessRuleViolationException.class);
    }
}
