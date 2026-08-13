package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.util.Locale;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(35)
class PptxTextExtractor implements TextExtractor {

    private static final String MIME = "application/vnd.openxmlformats-officedocument.presentationml.presentation";
    private static final int MAX_SLIDES = 500;
    private static final int MAX_SHAPES_PER_SLIDE = 500;
    private static final int MAX_TEXT_CHARACTERS = 8_000;
    private static final int MAX_OUTPUT_CHARACTERS = 1_000_000;

    @Override
    public boolean supports(AssetProcessingInput input) {
        return MIME.equalsIgnoreCase(input.mimeType())
                || input.originalFilename().toLowerCase(Locale.ROOT).endsWith(".pptx");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        try (XMLSlideShow presentation = new XMLSlideShow(content)) {
            StringBuilder output = new StringBuilder();
            int slideCount = Math.min(presentation.getSlides().size(), MAX_SLIDES);
            for (int slideIndex = 0; slideIndex < slideCount && output.length() < MAX_OUTPUT_CHARACTERS; slideIndex++) {
                var slide = presentation.getSlides().get(slideIndex);
                append(output, "Slide " + (slideIndex + 1));
                String title = slide.getTitle();
                if (title != null && !title.isBlank()) append(output, ": " + bound(title));
                append(output, "\n");
                int shapes = 0;
                for (XSLFShape shape : slide.getShapes()) {
                    if (shapes++ >= MAX_SHAPES_PER_SLIDE) break;
                    if (shape instanceof XSLFTextShape textShape) {
                        String text = textShape.getText();
                        if (text != null && !text.isBlank() && (title == null || !text.strip().equals(title.strip()))) {
                            append(output, bound(text) + "\n");
                        }
                    }
                }
            }
            return ExtractionResult.extracted(output.toString().strip(), "APACHE_POI_PPTX");
        } catch (Exception exception) {
            throw new TextExtractionException("PPTX text extraction failed", exception);
        }
    }

    private String bound(String value) {
        String normalized = value.strip();
        return normalized.substring(0, Math.min(normalized.length(), MAX_TEXT_CHARACTERS));
    }

    private void append(StringBuilder output, String value) {
        int remaining = MAX_OUTPUT_CHARACTERS - output.length();
        if (remaining > 0) output.append(value, 0, Math.min(value.length(), remaining));
    }
}
