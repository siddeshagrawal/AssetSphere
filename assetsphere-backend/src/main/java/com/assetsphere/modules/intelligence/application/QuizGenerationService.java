package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.intelligence.api.QuizDifficulty;
import com.assetsphere.modules.intelligence.api.QuizGenerationModel;
import com.assetsphere.modules.intelligence.api.QuizGenerationRequest;
import com.assetsphere.modules.intelligence.api.QuizGenerationResult;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringUnavailableException;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateQuizRequest;
import com.assetsphere.modules.intelligence.api.dto.response.QuizResponse;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidenceRetriever;
import com.assetsphere.modules.workspace.api.WorkspaceAccessFacade;
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
public class QuizGenerationService {
    private static final int MAX_CONTEXT = 30_000;
    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetReadFacade assets;
    private final ProcessedContentFacade content;
    private final WorkspaceSearchEvidenceRetriever evidence;
    private final ObjectProvider<QuizGenerationModel> models;
    private final BillingEntitlementFacade billing;
    private final AiModelCatalogService modelCatalog;

    public QuizResponse forAsset(UUID userId, UUID workspaceId, UUID assetId, int versionNumber, GenerateQuizRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        var asset = assets.getVersionMetadata(workspaceId, assetId, versionNumber);
        var processed = content.findByAssetVersionId(asset.assetVersionId())
                .filter(value -> value.workspaceId().equals(workspaceId) && value.hasUsableText())
                .orElseThrow(() -> new BusinessRuleViolationException("Asset version has no extracted text for quiz generation"));
        return generate(workspaceId, request, List.of(new QuizGenerationRequest.Source(
                "S1", bound(processed.extractedText(), MAX_CONTEXT))));
    }

    public QuizResponse forWorkspace(UUID userId, UUID workspaceId, GenerateQuizRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        String topic = request == null || request.topic() == null || request.topic().isBlank()
                ? "important workspace knowledge" : request.topic().trim();
        if (topic.length() > 200) throw new InvalidRequestException("Quiz topic must not exceed 200 characters");
        List<QuizGenerationRequest.Source> sources = new ArrayList<>();
        int remaining = MAX_CONTEXT;
        for (var item : evidence.retrieve(workspaceId, topic, 12)) {
            String text = bound(item.text(), Math.min(4_000, remaining));
            if (!text.isBlank()) { sources.add(new QuizGenerationRequest.Source("S" + (sources.size() + 1), text)); remaining -= text.length(); }
            if (remaining <= 0) break;
        }
        if (sources.isEmpty()) throw new BusinessRuleViolationException("Workspace has no relevant evidence for quiz generation");
        return generate(workspaceId, request, sources);
    }

    private QuizResponse generate(UUID workspaceId, GenerateQuizRequest request, List<QuizGenerationRequest.Source> sources) {
        int count = request == null || request.questionCount() == null ? 5 : request.questionCount();
        if (count < 3 || count > 15) throw new InvalidRequestException("Quiz question count must be between 3 and 15");
        QuizDifficulty difficulty = request == null || request.difficulty() == null ? QuizDifficulty.MEDIUM : request.difficulty();
        QuizGenerationModel model = models.getIfAvailable();
        if (model == null) throw new WorkspaceQuestionAnsweringUnavailableException("Quiz generation is unavailable", null);
        String selectedModel = modelCatalog.select(workspaceId, request == null ? null : request.modelId(), "QUIZ").modelId();
        billing.consume(workspaceId, UsageMetric.QUIZ_GENERATION);
        QuizGenerationResult result = model.generate(new QuizGenerationRequest(count, difficulty, selectedModel, sources));
        if (result == null || result.title() == null || result.title().isBlank()
                || result.questions() == null || result.questions().isEmpty())
            throw new WorkspaceQuestionAnsweringUnavailableException("Quiz provider returned an invalid result", null);
        Set<String> trusted = sources.stream().map(QuizGenerationRequest.Source::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<QuizResponse.Question> questions = result.questions().stream().limit(count).map(question -> {
            if (question.text() == null || question.text().isBlank()
                    || question.explanation() == null || question.explanation().isBlank()
                    || question.options() == null || question.options().size() != 4
                    || question.options().stream().anyMatch(value -> value == null || value.isBlank())
                    || question.options().stream().distinct().count() != 4
                    || question.correctAnswer() == null || !question.options().contains(question.correctAnswer()))
                throw new WorkspaceQuestionAnsweringUnavailableException("Quiz provider returned an invalid question", null);
            List<String> citations = trusted.stream().filter(id -> question.sourceIds() != null && question.sourceIds().contains(id)).toList();
            return new QuizResponse.Question(question.text(), "MULTIPLE_CHOICE", question.options(),
                    question.correctAnswer(), question.explanation(), citations);
        }).toList();
        return new QuizResponse(result.title().trim(), questions);
    }

    private String bound(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }
}
