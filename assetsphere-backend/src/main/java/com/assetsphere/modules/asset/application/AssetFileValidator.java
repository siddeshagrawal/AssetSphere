package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.api.AssetUploadProperties;

import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.common.exception.InvalidRequestException;
import com.assetsphere.modules.common.exception.PayloadTooLargeException;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@RequiredArgsConstructor
public class AssetFileValidator {

    private static final Map<String, Set<String>> EXTENSIONS_BY_MIME_TYPE = Map.ofEntries(
            Map.entry("application/pdf", Set.of(".pdf")),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", Set.of(".docx")),
            Map.entry("text/plain", Set.of(".txt", ".md")),
            Map.entry("text/markdown", Set.of(".md")),
            Map.entry("text/x-markdown", Set.of(".md")),
            Map.entry("text/csv", Set.of(".csv")),
            Map.entry("application/csv", Set.of(".csv")),
            Map.entry("application/vnd.ms-excel", Set.of(".csv")),
            Map.entry("application/json", Set.of(".json")),
            Map.entry("text/json", Set.of(".json")),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", Set.of(".xlsx")),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", Set.of(".pptx")),
            Map.entry("image/png", Set.of(".png")),
            Map.entry("image/jpeg", Set.of(".jpg", ".jpeg")),
            Map.entry("image/webp", Set.of(".webp")),
            Map.entry("video/mp4", Set.of(".mp4")),
            Map.entry("video/webm", Set.of(".webm"))
    );
    private static final Map<String, String> MIME_TYPE_BY_EXTENSION = Map.ofEntries(
            Map.entry(".pdf", "application/pdf"),
            Map.entry(".docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry(".txt", "text/plain"),
            Map.entry(".md", "text/markdown"),
            Map.entry(".csv", "text/csv"),
            Map.entry(".json", "application/json"),
            Map.entry(".xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry(".pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry(".png", "image/png"),
            Map.entry(".jpg", "image/jpeg"),
            Map.entry(".jpeg", "image/jpeg"),
            Map.entry(".webp", "image/webp"),
            Map.entry(".mp4", "video/mp4"),
            Map.entry(".webm", "video/webm")
    );

    private final AssetUploadProperties assetUploadProperties;

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("A non-empty file is required");
        }
        if (file.getSize() > assetUploadProperties.getMaxFileSize().toBytes()) {
            throw new PayloadTooLargeException("File exceeds the configured upload limit");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.indexOf('\u0000') >= 0
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new InvalidRequestException("Filename is unsafe");
        }

        String normalizedMimeType = normalizedMimeType(filename, file.getContentType());
        boolean allowed = assetUploadProperties.getAllowedMimeTypes().contains(normalizedMimeType)
                && hasAllowedExtension(filename, normalizedMimeType);
        if (!allowed && !assetUploadProperties.isAllowOtherTypes()) {
            throw new InvalidRequestException("Unsupported file type");
        }
        return new ValidatedFile(filename.trim(), normalizedMimeType, file.getSize(),
                classify(normalizedMimeType, allowed));
    }

    private AssetType classify(String mimeType, boolean allowed) {
        if (!allowed) {
            return AssetType.OTHER;
        }
        if ("application/pdf".equals(mimeType)) {
            return AssetType.PDF;
        }
        if (mimeType.contains("wordprocessingml")) {
            return AssetType.DOCX;
        }
        if (mimeType.startsWith("image/")) {
            return AssetType.IMAGE;
        }
        return AssetType.OTHER;
    }

    private boolean hasAllowedExtension(String filename, String mimeType) {
        Set<String> extensions = EXTENSIONS_BY_MIME_TYPE.get(mimeType);
        if (extensions == null) {
            return true;
        }
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        return extensions.stream().anyMatch(normalizedFilename::endsWith);
    }

    private String normalizedMimeType(String filename, String reportedMimeType) {
        if (reportedMimeType != null && !reportedMimeType.isBlank()
                && !"application/octet-stream".equalsIgnoreCase(reportedMimeType)) {
            return reportedMimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        }
        String normalizedFilename = filename.toLowerCase(Locale.ROOT);
        var inferredMimeType = MIME_TYPE_BY_EXTENSION.entrySet().stream()
                .filter(entry -> normalizedFilename.endsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
        if (inferredMimeType.isPresent()) {
            return inferredMimeType.get();
        }
        if (reportedMimeType != null && !reportedMimeType.isBlank()) {
            return reportedMimeType.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        }
        throw new InvalidRequestException("MIME type is required");
    }

    public record ValidatedFile(String filename, String mimeType, long size, AssetType assetType) {
    }
}
