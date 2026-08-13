package com.assetsphere.modules.intelligence.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import com.assetsphere.modules.intelligence.api.IntelligenceProvider;
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
@Table(name = "asset_intelligence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetIntelligence extends BaseEntity {

    private static final int MAX_FAILURE_CODE_LENGTH = 64;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "asset_version_id", nullable = false, unique = true)
    private UUID assetVersionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private IntelligenceStatus status;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private IntelligenceProvider provider;

    @Column(length = 128)
    private String model;

    @Column(name = "requested_model_id", length = 128)
    private String requestedModelId;

    @Column(columnDefinition = "text")
    private String summary;

    @Column(name = "key_points", columnDefinition = "text")
    private String keyPoints;

    @Column(columnDefinition = "text")
    private String tags;

    @Column(name = "input_characters", nullable = false)
    private int inputCharacters;

    @Column(name = "input_truncated", nullable = false)
    private boolean inputTruncated;

    @Column(name = "failure_code", length = MAX_FAILURE_CODE_LENGTH)
    private String failureCode;

    @Column(name = "failure_message", length = MAX_FAILURE_MESSAGE_LENGTH)
    private String failureMessage;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static AssetIntelligence pending(UUID workspaceId, UUID assetId, UUID assetVersionId) {
        AssetIntelligence intelligence = new AssetIntelligence();
        intelligence.workspaceId = require(workspaceId, "Workspace is required");
        intelligence.assetId = require(assetId, "Asset is required");
        intelligence.assetVersionId = require(assetVersionId, "Asset version is required");
        intelligence.status = IntelligenceStatus.PENDING;
        intelligence.keyPoints = "[]";
        intelligence.tags = "[]";
        return intelligence;
    }

    public static AssetIntelligence requested(
            UUID workspaceId, UUID assetId, UUID assetVersionId, Instant now
    ) {
        return requested(workspaceId, assetId, assetVersionId, now, null);
    }

    public static AssetIntelligence requested(
            UUID workspaceId, UUID assetId, UUID assetVersionId, Instant now, String requestedModelId
    ) {
        AssetIntelligence intelligence = pending(workspaceId, assetId, assetVersionId);
        intelligence.request(now, requestedModelId);
        return intelligence;
    }

    public void request(Instant now) {
        request(now, null);
    }

    public void request(Instant now, String requestedModelId) {
        requireStatus(IntelligenceStatus.PENDING, "Only pending intelligence can be requested");
        this.requestedModelId = optionalText(requestedModelId, 128);
        startedAt = require(now, "Request time is required");
        status = IntelligenceStatus.PROCESSING;
    }

    public void retry(Instant now) {
        retry(now, requestedModelId);
    }

    public void retry(Instant now, String requestedModelId) {
        requireStatus(IntelligenceStatus.FAILED, "Only failed intelligence can be retried");
        this.requestedModelId = optionalText(requestedModelId, 128);
        provider = null;
        model = null;
        summary = null;
        keyPoints = "[]";
        tags = "[]";
        inputCharacters = 0;
        inputTruncated = false;
        failureCode = null;
        failureMessage = null;
        completedAt = null;
        startedAt = require(now, "Retry time is required");
        status = IntelligenceStatus.PROCESSING;
    }

    public void start(IntelligenceProvider provider, String model, int inputCharacters, boolean inputTruncated, Instant now) {
        if (status != IntelligenceStatus.PENDING && status != IntelligenceStatus.PROCESSING) {
            throw new BusinessRuleViolationException("Only requested intelligence can start");
        }
        this.provider = require(provider, "Provider is required");
        this.model = requiredText(model, "Model is required", 128);
        if (inputCharacters <= 0) {
            throw new BusinessRuleViolationException("Intelligence input must not be empty");
        }
        this.inputCharacters = inputCharacters;
        this.inputTruncated = inputTruncated;
        this.startedAt = require(now, "Start time is required");
        this.status = IntelligenceStatus.PROCESSING;
    }

    public void complete(String summary, String keyPoints, String tags, Instant now) {
        requireStatus(IntelligenceStatus.PROCESSING, "Only processing intelligence can complete");
        this.summary = requiredText(summary, "Summary is required", Integer.MAX_VALUE);
        this.keyPoints = requiredText(keyPoints, "Key points are required", Integer.MAX_VALUE);
        this.tags = requiredText(tags, "Tags are required", Integer.MAX_VALUE);
        this.completedAt = require(now, "Completion time is required");
        this.failureCode = null;
        this.failureMessage = null;
        this.status = IntelligenceStatus.READY;
    }

    public void markNotApplicable(Instant now) {
        requireRequested("Only requested intelligence can be marked not applicable");
        completeTerminal(IntelligenceStatus.NOT_APPLICABLE, now);
    }

    public void disable(Instant now) {
        requireRequested("Only requested intelligence can be disabled");
        completeTerminal(IntelligenceStatus.DISABLED, now);
    }

    public void fail(String code, String message, Instant now) {
        if (status != IntelligenceStatus.PENDING && status != IntelligenceStatus.PROCESSING) {
            throw new BusinessRuleViolationException("Only pending or processing intelligence can fail");
        }
        failureCode = requiredText(code, "Failure code is required", MAX_FAILURE_CODE_LENGTH);
        failureMessage = bound(message, MAX_FAILURE_MESSAGE_LENGTH);
        completedAt = require(now, "Failure time is required");
        status = IntelligenceStatus.FAILED;
    }

    public boolean isTerminal() {
        return status == IntelligenceStatus.READY || status == IntelligenceStatus.FAILED
                || status == IntelligenceStatus.NOT_APPLICABLE || status == IntelligenceStatus.DISABLED;
    }

    private void completeTerminal(IntelligenceStatus terminalStatus, Instant now) {
        completedAt = require(now, "Completion time is required");
        status = terminalStatus;
    }

    private void requireStatus(IntelligenceStatus expected, String message) {
        if (status != expected) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private void requireRequested(String message) {
        if (status != IntelligenceStatus.PENDING && status != IntelligenceStatus.PROCESSING) {
            throw new BusinessRuleViolationException(message);
        }
    }

    private static String requiredText(String value, String message, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new BusinessRuleViolationException(message);
        }
        String normalized = value.strip();
        if (normalized.length() > maxLength) {
            throw new BusinessRuleViolationException(message);
        }
        return normalized;
    }

    private static String bound(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.strip();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private static String optionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.strip();
        if (normalized.length() > maxLength) throw new BusinessRuleViolationException("Requested model is invalid");
        return normalized;
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
