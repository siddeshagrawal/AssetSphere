package com.assetsphere.modules.search.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_semantic_indexes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetSemanticIndex extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;
    @Column(name = "asset_id", nullable = false)
    private UUID assetId;
    @Column(name = "asset_version_id", nullable = false, unique = true)
    private UUID assetVersionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SemanticIndexStatus status;
    @Column(name = "embedding_model")
    private String embeddingModel;
    @Column(name = "embedding_dimension")
    private Integer embeddingDimension;
    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;
    @Column(name = "failure_code")
    private String failureCode;
    @Column(name = "failure_message")
    private String failureMessage;
    @Column(name = "started_at")
    private Instant startedAt;
    @Column(name = "completed_at")
    private Instant completedAt;

    public static AssetSemanticIndex pending(UUID workspaceId, UUID assetId, UUID assetVersionId) {
        AssetSemanticIndex index = new AssetSemanticIndex();
        index.workspaceId = require(workspaceId, "Workspace is required");
        index.assetId = require(assetId, "Asset is required");
        index.assetVersionId = require(assetVersionId, "Asset version is required");
        index.status = SemanticIndexStatus.PENDING;
        return index;
    }

    public void start(String model, int dimension, Instant now) {
        requireStatus(SemanticIndexStatus.PENDING);
        if (model == null || model.isBlank() || dimension <= 0) throw new BusinessRuleViolationException("Embedding configuration is invalid");
        embeddingModel = model;
        embeddingDimension = dimension;
        startedAt = require(now, "Start time is required");
        status = SemanticIndexStatus.PROCESSING;
    }

    public void complete(int chunks, Instant now) {
        requireStatus(SemanticIndexStatus.PROCESSING);
        if (chunks <= 0) throw new BusinessRuleViolationException("Semantic index requires chunks");
        chunkCount = chunks;
        completedAt = require(now, "Completion time is required");
        status = SemanticIndexStatus.READY;
    }

    public void notApplicable(Instant now) { terminalFromPending(SemanticIndexStatus.NOT_APPLICABLE, now); }
    public void disable(Instant now) { terminalFromPending(SemanticIndexStatus.DISABLED, now); }

    public void fail(String code, String message, Instant now) {
        if (status != SemanticIndexStatus.PENDING && status != SemanticIndexStatus.PROCESSING) {
            throw new BusinessRuleViolationException("Only pending or processing semantic indexes can fail");
        }
        failureCode = bounded(code, 64);
        failureMessage = bounded(message, 1000);
        completedAt = require(now, "Failure time is required");
        status = SemanticIndexStatus.FAILED;
    }

    public void prepareRetry() {
        if (status == SemanticIndexStatus.PENDING || status == SemanticIndexStatus.PROCESSING) return;
        requireStatus(SemanticIndexStatus.FAILED);
        status = SemanticIndexStatus.PENDING;
        embeddingModel = null;
        embeddingDimension = null;
        chunkCount = 0;
        failureCode = null;
        failureMessage = null;
        startedAt = null;
        completedAt = null;
    }

    public boolean isTerminal() { return status != SemanticIndexStatus.PENDING && status != SemanticIndexStatus.PROCESSING; }

    private void terminalFromPending(SemanticIndexStatus terminal, Instant now) {
        requireStatus(SemanticIndexStatus.PENDING);
        completedAt = require(now, "Completion time is required");
        status = terminal;
    }
    private void requireStatus(SemanticIndexStatus expected) { if (status != expected) throw new BusinessRuleViolationException("Invalid semantic index transition"); }
    private static String bounded(String value, int max) { return value == null ? null : value.strip().substring(0, Math.min(value.strip().length(), max)); }
    private static <T> T require(T value, String message) { if (value == null) throw new BusinessRuleViolationException(message); return value; }
}
