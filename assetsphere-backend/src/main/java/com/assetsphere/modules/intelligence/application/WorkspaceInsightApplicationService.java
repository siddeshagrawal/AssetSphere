package com.assetsphere.modules.intelligence.application;

import com.assetsphere.modules.asset.api.AssetMetadataSnapshot;
import com.assetsphere.modules.asset.api.AssetReadFacade;
import com.assetsphere.modules.billing.api.BillingEntitlementFacade;
import com.assetsphere.modules.billing.api.UsageMetric;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.intelligence.api.IntelligenceProperties;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightModel;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightRequest;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightResult;
import com.assetsphere.modules.intelligence.api.WorkspaceInsightType;
import com.assetsphere.modules.intelligence.api.WorkspaceQuestionAnsweringUnavailableException;
import com.assetsphere.modules.intelligence.api.dto.request.GenerateWorkspaceInsightRequest;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceAnswerCitationResponse;
import com.assetsphere.modules.intelligence.api.dto.response.WorkspaceInsightResponse;
import com.assetsphere.modules.processing.api.ProcessedContentFacade;
import com.assetsphere.modules.search.api.WorkspaceSearchEvidence;
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
public class WorkspaceInsightApplicationService {
    private static final int MAX_ITEMS = 12;
    private static final int MAX_ITEM_TEXT = 1_000;
    private final WorkspaceAccessFacade workspaceAccess;
    private final AssetReadFacade assets;
    private final ProcessedContentFacade content;
    private final WorkspaceSearchEvidenceRetriever evidence;
    private final ObjectProvider<WorkspaceInsightModel> models;
    private final BillingEntitlementFacade billing;
    private final AiModelCatalogService modelCatalog;
    private final IntelligenceProperties properties;

    public WorkspaceInsightResponse forWorkspace(
            UUID userId, UUID workspaceId, GenerateWorkspaceInsightRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        validate(request);
        String focus = normalizedFocus(request);
        List<TrustedSource> sources = workspaceSources(workspaceId, focus);
        return generate(workspaceId, request, sources);
    }

    public WorkspaceInsightResponse forAsset(
            UUID userId, UUID workspaceId, UUID assetId, int versionNumber,
            GenerateWorkspaceInsightRequest request) {
        workspaceAccess.requireActiveMembership(workspaceId, userId);
        validate(request);
        AssetMetadataSnapshot asset = assets.getVersionMetadata(workspaceId, assetId, versionNumber);
        var processed = content.findByAssetVersionId(asset.assetVersionId())
                .filter(value -> value.workspaceId().equals(workspaceId) && value.hasUsableText())
                .orElseThrow(() -> new BusinessRuleViolationException(
                        "Asset version has no extracted text for insight generation"));
        int limit = Math.max(1, properties.getMaxInputCharacters());
        String text = bound(processed.extractedText(), limit);
        TrustedSource source = new TrustedSource(
                new WorkspaceInsightRequest.Source("S1", text),
                asset.assetId(), asset.assetVersionId(), asset.displayName(), asset.originalFilename(), null, text);
        return generate(workspaceId, request, List.of(source));
    }

    private WorkspaceInsightResponse generate(
            UUID workspaceId, GenerateWorkspaceInsightRequest request, List<TrustedSource> sources) {
        WorkspaceInsightModel model = models.getIfAvailable();
        if (model == null) {
            throw new WorkspaceQuestionAnsweringUnavailableException("Insight generation is unavailable", null);
        }
        String modelId = modelCatalog.select(workspaceId, request.modelId(), "INTELLIGENCE").modelId();
        billing.consume(workspaceId, UsageMetric.AI_INSIGHT);
        WorkspaceInsightResult result = model.generate(new WorkspaceInsightRequest(
                request.type(), normalizedFocus(request), modelId,
                sources.stream().map(TrustedSource::modelSource).toList()));
        return sanitize(request.type(), sources, result);
    }

    private WorkspaceInsightResponse sanitize(
            WorkspaceInsightType type, List<TrustedSource> sources, WorkspaceInsightResult result) {
        if (result == null || result.summary() == null || result.summary().isBlank() || result.items() == null) {
            throw new WorkspaceQuestionAnsweringUnavailableException(
                    "Insight provider returned an invalid result", null);
        }
        Set<String> cited = new LinkedHashSet<>();
        List<WorkspaceInsightResponse.Item> items = result.items().stream()
                .filter(item -> item != null && item.title() != null && !item.title().isBlank())
                .limit(MAX_ITEMS)
                .map(item -> {
                    List<String> trustedIds = sources.stream().map(source -> source.modelSource().id())
                            .filter(id -> item.sourceIds() != null && item.sourceIds().contains(id)).toList();
                    cited.addAll(trustedIds);
                    return new WorkspaceInsightResponse.Item(
                            bound(item.title(), MAX_ITEM_TEXT), boundNullable(item.secondary(), MAX_ITEM_TEXT),
                            boundNullable(item.detail(), MAX_ITEM_TEXT), boundNullable(item.severity(), 24), trustedIds);
                }).toList();
        List<WorkspaceAnswerCitationResponse> citations = sources.stream()
                .filter(source -> cited.contains(source.modelSource().id()))
                .map(source -> new WorkspaceAnswerCitationResponse(
                        source.modelSource().id(), source.assetId(), source.assetVersionId(), source.title(),
                        source.filename(), source.chunkOrdinal(), source.snippet()))
                .toList();
        return new WorkspaceInsightResponse(type.name(),
                bound(result.summary(), properties.getMaxSummaryCharacters()), items, citations);
    }

    private List<TrustedSource> workspaceSources(UUID workspaceId, String focus) {
        List<TrustedSource> sources = new ArrayList<>();
        int remaining = Math.max(1, properties.getMaxRagContextCharacters());
        int limit = Math.min(20, Math.max(1, properties.getMaxRagSources()));
        for (WorkspaceSearchEvidence item : evidence.retrieve(workspaceId, focus, limit)) {
            int allowed = Math.min(Math.max(1, properties.getMaxRagSourceCharacters()), remaining);
            String text = bound(item.text(), allowed);
            if (text.isBlank()) continue;
            String id = "S" + (sources.size() + 1);
            sources.add(new TrustedSource(new WorkspaceInsightRequest.Source(id, text), item.assetId(),
                    item.assetVersionId(), item.title(), item.filename(), item.chunkOrdinal(), text));
            remaining -= text.length();
            if (remaining <= 0) break;
        }
        if (sources.isEmpty()) {
            throw new BusinessRuleViolationException("Workspace has no relevant evidence for insight generation");
        }
        return List.copyOf(sources);
    }

    private void validate(GenerateWorkspaceInsightRequest request) {
        if (request == null || request.type() == null) throw new InvalidRequestException("Insight type is required");
        if (request.type() == WorkspaceInsightType.KNOWLEDGE_CHECK) {
            throw new InvalidRequestException("Knowledge Check uses the existing quiz generation endpoint");
        }
    }

    private String normalizedFocus(GenerateWorkspaceInsightRequest request) {
        if (request.focus() != null && !request.focus().isBlank()) return request.focus().trim();
        return request.type().name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
    }

    private String bound(String value, int limit) {
        String normalized = value == null ? "" : value.trim();
        return normalized.length() > limit ? normalized.substring(0, limit) : normalized;
    }

    private String boundNullable(String value, int limit) {
        String bounded = bound(value, limit);
        return bounded.isBlank() ? null : bounded;
    }

    private record TrustedSource(
            WorkspaceInsightRequest.Source modelSource, UUID assetId, UUID assetVersionId,
            String title, String filename, Integer chunkOrdinal, String snippet) { }
}
