package com.assetsphere.modules.intelligence.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.assetsphere.modules.common.exception.ResourceNotFoundException;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.billing.api.Plan;
import com.assetsphere.modules.intelligence.api.AiModelDescriptor;
import java.util.Set;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.RagRateLimiter;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringModel;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringRequest;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringResult;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringUnavailableException;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
class WorkspaceRagApplicationServiceTests {

    @Mock WorkspaceAccessFacade workspaceAccess;
    @Mock RagRateLimiter rateLimiter;
    @Mock WorkspaceSearchEvidenceRetriever evidenceRetriever;
    @Mock ObjectProvider<WorkspaceQuestionAnsweringModel> models;
    @Mock WorkspaceQuestionAnsweringModel model;
    @Mock BillingEntitlementFacade billing;
    @Mock AiModelCatalogService modelCatalog;

    @Test
    void noEvidenceReturnsDeterministicAnswerWithoutCallingModel() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        when(evidenceRetriever.retrieve(workspaceId, "What is the policy?", 8)).thenReturn(List.of());

        var response = service().ask(userId, workspaceId, "  What is the policy?  ");

        assertThat(response.answer()).isEqualTo(WorkspaceRagApplicationService.NO_EVIDENCE_ANSWER);
        assertThat(response.citations()).isEmpty();
        verify(rateLimiter).check(workspaceId, userId);
        verify(models, never()).getIfAvailable();
        verify(model, never()).answer(any());
        verify(billing, never()).consume(any(), any());
    }

    @Test
    void assignsSourcesOnceAndBuildsDeduplicatedTrustedCitationsInSourceOrder() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        WorkspaceSearchEvidence first = evidence("Trusted title one", "one.txt", "First trusted text");
        WorkspaceSearchEvidence second = evidence("Trusted title two", "two.txt", "Second trusted text");
        when(evidenceRetriever.retrieve(workspaceId, "question", 8)).thenReturn(List.of(first, second));
        when(models.getIfAvailable()).thenReturn(model);
        when(modelCatalog.select(workspaceId, null, "ASK")).thenReturn(model("ASK"));
        when(model.answer(any())).thenReturn(new WorkspaceQuestionAnsweringResult(
                "Grounded answer", List.of("S2", "UNKNOWN", "S1", "S2")));
        ArgumentCaptor<WorkspaceQuestionAnsweringRequest> request =
                ArgumentCaptor.forClass(WorkspaceQuestionAnsweringRequest.class);

        var response = service().ask(userId, workspaceId, "question");

        verify(model).answer(request.capture());
        assertThat(request.getValue().modelId()).isEqualTo("gpt-4o-mini");
        assertThat(request.getValue().sources()).extracting(source -> source.id())
                .containsExactly("S1", "S2");
        assertThat(request.getValue().sources()).extracting(source -> source.text())
                .containsExactly("First trusted text", "Second trusted text");
        assertThat(response.citations()).extracting(citation -> citation.sourceId())
                .containsExactly("S1", "S2");
        assertThat(response.citations()).extracting(citation -> citation.title())
                .containsExactly("Trusted title one", "Trusted title two");
        assertThat(response.citations()).extracting(citation -> citation.assetId())
                .containsExactly(first.assetId(), second.assetId());
        verify(rateLimiter).check(workspaceId, userId);
        verify(evidenceRetriever).retrieve(workspaceId, "question", 8);
        verify(billing).consume(workspaceId, UsageMetric.ASK);
    }

    @Test
    void unauthorizedWorkspaceIsRejectedBeforeLimiterRetrievalOrProvider() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Workspace not found"))
                .when(workspaceAccess).requireActiveMembership(workspaceId, userId);

        assertThatThrownBy(() -> service().ask(userId, workspaceId, "question"))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(rateLimiter, never()).check(any(), any());
        verify(evidenceRetriever, never()).retrieve(any(), any(), eq(8));
        verify(models, never()).getIfAvailable();
    }

    @Test
    void providerFailurePreservesTypedUnavailableException() {
        UUID userId = UUID.randomUUID();
        UUID workspaceId = UUID.randomUUID();
        WorkspaceQuestionAnsweringUnavailableException failure =
                new WorkspaceQuestionAnsweringUnavailableException("provider unavailable", null);
        when(evidenceRetriever.retrieve(workspaceId, "question", 8))
                .thenReturn(List.of(evidence("Title", "file.txt", "Evidence")));
        when(models.getIfAvailable()).thenReturn(model);
        when(modelCatalog.select(workspaceId, null, "ASK")).thenReturn(model("ASK"));
        when(model.answer(any())).thenThrow(failure);

        assertThatThrownBy(() -> service().ask(userId, workspaceId, "question"))
                .isSameAs(failure);
    }

    private WorkspaceRagApplicationService service() {
        return new WorkspaceRagApplicationService(
                workspaceAccess, rateLimiter, evidenceRetriever, models, new IntelligenceProperties(), billing, modelCatalog);
    }

    private AiModelDescriptor model(String capability) {
        return new AiModelDescriptor("OPENAI", "gpt-4o-mini", "Default", Set.of(capability), Plan.FREE, true);
    }

    private WorkspaceSearchEvidence evidence(String title, String filename, String text) {
        return new WorkspaceSearchEvidence(
                UUID.randomUUID(), UUID.randomUUID(), title, filename, null, text);
    }
}
