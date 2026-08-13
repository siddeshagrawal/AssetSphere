package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.RagRateLimiter;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringModel;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringRequest;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringResult;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringSource;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringUnavailableException;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceAnswerCitationResponse;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceQuestionAnswerResponse;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class WorkspaceRagApplicationService {

    public static final String NO_EVIDENCE_ANSWER =
            "I couldn't find enough information in this workspace to answer that.";
    private static final int MAX_QUESTION_CHARACTERS = 200;

    private final WorkspaceAccessFacade workspaceAccess;
    private final RagRateLimiter rateLimiter;
    private final WorkspaceSearchEvidenceRetriever evidenceRetriever;
    private final ObjectProvider<WorkspaceQuestionAnsweringModel> models;
    private final IntelligenceProperties properties;
    private final BillingEntitlementFacade billing;
    private final AiModelCatalogService modelCatalog;

    public WorkspaceQuestionAnswerResponse ask(UUID userId, UUID workspaceId, String question) {
        return ask(userId, workspaceId, question, null);
    }

    public WorkspaceQuestionAnswerResponse ask(UUID userId, UUID workspaceId, String question, String modelId) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        String normalizedQuestion = validateQuestion(question);
        rateLimiter.check(workspaceId, userId);

        int retrievalLimit = Math.min(20, Math.max(1, properties.getMaxRagSources()));
        List<TrustedSource> sources = assignSources(evidenceRetriever.retrieve(
                workspaceId, normalizedQuestion, retrievalLimit));
        if (sources.isEmpty()) {
            return new WorkspaceQuestionAnswerResponse(NO_EVIDENCE_ANSWER, List.of());
        }

        WorkspaceQuestionAnsweringModel model = models.getIfAvailable();
        if (model == null) {
            throw new WorkspaceQuestionAnsweringUnavailableException(
                    "Workspace question answering is unavailable", null);
        }
        String selectedModel = modelCatalog.select(workspaceId, modelId, "ASK").modelId();
        billing.consume(workspaceId, UsageMetric.ASK);
        WorkspaceQuestionAnsweringResult result = model.answer(new WorkspaceQuestionAnsweringRequest(
                normalizedQuestion, selectedModel, sources.stream().map(TrustedSource::modelSource).toList()));
        if (result == null || result.answer() == null || result.answer().isBlank()) {
            throw new WorkspaceQuestionAnsweringUnavailableException(
                    "Workspace question answering returned an invalid response", null);
        }
        return new WorkspaceQuestionAnswerResponse(result.answer().trim(), trustedCitations(sources, result));
    }

    private List<TrustedSource> assignSources(List<WorkspaceSearchEvidence> evidence) {
        List<TrustedSource> sources = new ArrayList<>();
        int remainingCharacters = properties.getMaxRagContextCharacters();
        for (WorkspaceSearchEvidence item : evidence) {
            if (sources.size() >= properties.getMaxRagSources() || remainingCharacters <= 0) {
                break;
            }
            String text = item.text() == null ? "" : item.text().trim();
            int allowedCharacters = Math.min(properties.getMaxRagSourceCharacters(), remainingCharacters);
            if (text.length() > allowedCharacters) {
                text = text.substring(0, allowedCharacters);
            }
            if (text.isBlank()) {
                continue;
            }
            String sourceId = "S" + (sources.size() + 1);
            WorkspaceQuestionAnsweringSource modelSource = new WorkspaceQuestionAnsweringSource(
                    sourceId, text);
            sources.add(new TrustedSource(modelSource, item, text));
            remainingCharacters -= text.length();
        }
        return List.copyOf(sources);
    }

    private List<WorkspaceAnswerCitationResponse> trustedCitations(
            List<TrustedSource> sources, WorkspaceQuestionAnsweringResult result) {
        Set<String> citedIds = new LinkedHashSet<>(result.citedSourceIds());
        return sources.stream()
                .filter(source -> citedIds.contains(source.modelSource().id()))
                .map(source -> new WorkspaceAnswerCitationResponse(
                        source.modelSource().id(), source.evidence().assetId(), source.evidence().assetVersionId(),
                        source.evidence().title(), source.evidence().filename(), source.evidence().chunkOrdinal(),
                        source.boundedText()))
                .toList();
    }

    private String validateQuestion(String question) {
        if (question == null || question.isBlank() || question.trim().length() > MAX_QUESTION_CHARACTERS) {
            throw new InvalidRequestException("Question must contain 1 to 200 characters");
        }
        return question.trim();
    }

    private record TrustedSource(
            WorkspaceQuestionAnsweringSource modelSource,
            WorkspaceSearchEvidence evidence,
            String boundedText
    ) {
    }
}
