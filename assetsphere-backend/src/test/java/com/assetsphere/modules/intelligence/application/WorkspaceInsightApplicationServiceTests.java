package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.intelligence.api.AiModelDescriptor;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightModel;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightRequest;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightResult;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightType;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateWorkspaceInsightRequest;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkspaceInsightApplicationServiceTests {
    @Mock WorkspaceAccessFacade workspaceAccess;
    @Mock AssetReadFacade assets;
    @Mock ProcessedContentFacade content;
    @Mock WorkspaceSearchEvidenceRetriever evidence;
    @Mock ObjectProvider<WorkspaceInsightModel> models;
    @Mock WorkspaceInsightModel model;
    @Mock BillingEntitlementFacade billing;
    @Mock AiModelCatalogService modelCatalog;

    @Test
    void noEvidenceDoesNotInvokeOrChargeProvider() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(evidence.retrieve(workspaceId, "operational risks", 8)).thenReturn(List.of());

        assertThatThrownBy(() -> service().forWorkspace(userId, workspaceId,
                request(WorkspaceInsightType.RISKS_AND_GAPS, "operational risks")))
                .isInstanceOf(BusinessRuleViolationException.class);

        verify(models, never()).getIfAvailable();
        verify(model, never()).generate(any());
        verify(billing, never()).consume(any(), any());
    }

    @Test
    void assignsDeterministicSourcesAndFiltersCitationsFromTrustedEvidence() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceSearchEvidence first = evidence("Policy", "policy.pdf", "First evidence");
        WorkspaceSearchEvidence second = evidence("Minutes", "minutes.docx", "Second evidence");
        when(evidence.retrieve(workspaceId, "key decisions", 8)).thenReturn(List.of(first, second));
        when(models.getIfAvailable()).thenReturn(model);
        when(modelCatalog.select(workspaceId, null, "INTELLIGENCE")).thenReturn(
                new AiModelDescriptor("OPENAI", "gpt-4o-mini", "Default",
                        Set.of("INTELLIGENCE"), Plan.FREE, true));
        when(model.generate(any())).thenReturn(new WorkspaceInsightResult("Summary", List.of(
                new WorkspaceInsightResult.Item("Decision", null, "Rationale", null,
                        List.of("S2", "UNKNOWN", "S2", "S1")))));
        ArgumentCaptor<WorkspaceInsightRequest> request = ArgumentCaptor.forClass(WorkspaceInsightRequest.class);

        var response = service().forWorkspace(UUID.randomUUID(), workspaceId,
                request(WorkspaceInsightType.KEY_DECISIONS, null));

        verify(model).generate(request.capture());
        assertThat(request.getValue().sources()).extracting(WorkspaceInsightRequest.Source::id)
                .containsExactly("S1", "S2");
        assertThat(response.items().get(0).sourceIds()).containsExactly("S1", "S2");
        assertThat(response.citations()).extracting(citation -> citation.sourceId())
                .containsExactly("S1", "S2");
        assertThat(response.citations()).extracting(citation -> citation.assetId())
                .containsExactly(first.assetId(), second.assetId());
        verify(billing).consume(workspaceId, UsageMetric.AI_INSIGHT);
    }

    private WorkspaceInsightApplicationService service() {
        return new WorkspaceInsightApplicationService(workspaceAccess, assets, content, evidence, models,
                billing, modelCatalog, new IntelligenceProperties());
    }

    private GenerateWorkspaceInsightRequest request(WorkspaceInsightType type, String focus) {
        return new GenerateWorkspaceInsightRequest(type, focus, null);
    }

    private WorkspaceSearchEvidence evidence(String title, String filename, String text) {
        return new WorkspaceSearchEvidence(UUID.randomUUID(), UUID.randomUUID(), title, filename, null, text);
    }
}
