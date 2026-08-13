package com.assetsphere.modules.processing.content.domain;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.common.persistence.BaseEntity;
import com.assetsphere.modules.processing.text.ExtractionResult;
import com.assetsphere.modules.processing.text.ExtractionStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "asset_text_contents")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AssetTextContent extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "asset_version_id", nullable = false, unique = true)
    private UUID assetVersionId;

    @Column(name = "extracted_text", nullable = false, columnDefinition = "text")
    private String extractedText;

    @Column(name = "character_count", nullable = false)
    private int characterCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "extraction_status", nullable = false, length = 32)
    private ExtractionStatus extractionStatus;

    @Column(name = "extractor_type", nullable = false, length = 32)
    private String extractorType;

    @Column(nullable = false)
    private boolean truncated;

    public static AssetTextContent create(UUID workspaceId, UUID assetId, UUID assetVersionId, ExtractionResult result) {
        AssetTextContent content = new AssetTextContent();
        content.workspaceId = required(workspaceId, "Workspace is required");
        content.assetId = required(assetId, "Asset is required");
        content.assetVersionId = required(assetVersionId, "Asset version is required");
        content.apply(result);
        return content;
    }

    public void replace(ExtractionResult result) {
        apply(result);
    }

    private void apply(ExtractionResult result) {
        if (result == null || result.status() == null || result.extractorType() == null || result.extractorType().isBlank()) {
            throw new BusinessRuleViolationException("Extraction result is invalid");
        }
        extractedText = sanitize(result.text());
        characterCount = extractedText.length();
        extractionStatus = result.status();
        extractorType = result.extractorType();
        truncated = result.truncated();
    }

    private static String sanitize(String text) {
        return text == null ? "" : text.replace("\u0000", "");
    }

    private static <T> T required(T value, String message) {
        if (value == null) {
            throw new BusinessRuleViolationException(message);
        }
        return value;
    }
}
