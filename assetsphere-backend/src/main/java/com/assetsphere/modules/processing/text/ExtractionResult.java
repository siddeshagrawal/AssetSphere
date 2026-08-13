package com.assetsphere.modules.processing.text;

public record ExtractionResult(String text, ExtractionStatus status, String extractorType, boolean truncated) {

    public static ExtractionResult extracted(String text, String extractorType) {
        return new ExtractionResult(text, text == null || text.isBlank() ? ExtractionStatus.NO_TEXT_EXTRACTED : ExtractionStatus.EXTRACTED,
                extractorType, false);
    }

    public static ExtractionResult notApplicable(String extractorType) {
        return new ExtractionResult("", ExtractionStatus.NOT_APPLICABLE, extractorType, false);
    }

    public static ExtractionResult unsupported() {
        return new ExtractionResult("", ExtractionStatus.UNSUPPORTED, "NONE", false);
    }

    public ExtractionResult withNormalizedText(String value, boolean wasTruncated) {
        return new ExtractionResult(value, value.isBlank() && status == ExtractionStatus.EXTRACTED
                ? ExtractionStatus.NO_TEXT_EXTRACTED : status, extractorType, wasTruncated);
    }
}
