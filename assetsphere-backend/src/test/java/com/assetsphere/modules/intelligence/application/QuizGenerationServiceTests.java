package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.intelligence.api.QuizDifficulty;
import com.assetsphere.modules.intelligence.api.QuizGenerationModel;
import com.assetsphere.modules.intelligence.api.QuizGenerationResult;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateQuizRequest;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class QuizGenerationServiceTests {
    @Test
    @SuppressWarnings("unchecked")
    void generatesGroundedQuizAndRemovesUnknownSourceIds() {
        UUID workspaceId = UUID.randomUUID();
        WorkspaceSearchEvidenceRetriever evidence = mock(WorkspaceSearchEvidenceRetriever.class);
        when(evidence.retrieve(workspaceId, "security", 12)).thenReturn(List.of(new WorkspaceSearchEvidence(
                UUID.randomUUID(), UUID.randomUUID(), "Title", "file.pdf", 0, "trusted text")));
        QuizGenerationModel model = mock(QuizGenerationModel.class);
        when(model.generate(org.mockito.ArgumentMatchers.any())).thenReturn(new QuizGenerationResult("Quiz", List.of(
                new QuizGenerationResult.Question("Question?", List.of("A", "B", "C", "D"), "A",
                        "Because", List.of("S1", "UNKNOWN")))));
        ObjectProvider<QuizGenerationModel> models = mock(ObjectProvider.class);
        when(models.getIfAvailable()).thenReturn(model);
        BillingEntitlementFacade billing = mock(BillingEntitlementFacade.class);
        AiModelCatalogService catalog = mock(AiModelCatalogService.class);
        when(catalog.select(workspaceId, null, "QUIZ")).thenReturn(new com.assetsphere.modules.intelligence.api.AiModelDescriptor(
                "OPENAI", "gpt-4o-mini", "Default", java.util.Set.of("QUIZ"), com.assetsphere.modules.billing.api.Plan.FREE, true));
        QuizGenerationService service = new QuizGenerationService(mock(WorkspaceAccessFacade.class),
                mock(AssetReadFacade.class), mock(ProcessedContentFacade.class), evidence, models, billing, catalog);

        var result = service.forWorkspace(UUID.randomUUID(), workspaceId,
                new GenerateQuizRequest(5, QuizDifficulty.MEDIUM, "security", null));

        assertThat(result.questions()).hasSize(1);
        assertThat(result.questions().getFirst().sourceIds()).containsExactly("S1");
        verify(billing).consume(workspaceId, UsageMetric.QUIZ_GENERATION);
    }
}
