package com.assetsphere.modules.asset.application;

import com.assetsphere.modules.asset.domain.AssetType;
import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class AssetFileValidator {

    private static final Set<String> SUPPORTED_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "image/png",
            "image/jpeg",
            "image/webp"
    );

    public ValidatedFile validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessRuleViolationException("A non-empty file is required");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank() || filename.indexOf('\u0000') >= 0
                || filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new BusinessRuleViolationException("Filename is unsafe");
        }

        String mimeType = file.getContentType();
        if (mimeType == null || !SUPPORTED_TYPES.contains(mimeType.toLowerCase(Locale.ROOT))) {
            throw new BusinessRuleViolationException("Unsupported file type");
        }

        String normalizedMimeType = mimeType.toLowerCase(Locale.ROOT);
        return new ValidatedFile(filename.trim(), normalizedMimeType, file.getSize(), classify(normalizedMimeType));
    }

    private AssetType classify(String mimeType) {
        if ("application/pdf".equals(mimeType)) {
            return AssetType.PDF;
        }
        if (mimeType.contains("wordprocessingml")) {
            return AssetType.DOCX;
        }
        return AssetType.IMAGE;
    }

    public record ValidatedFile(String filename, String mimeType, long size, AssetType assetType) {
    }
}
