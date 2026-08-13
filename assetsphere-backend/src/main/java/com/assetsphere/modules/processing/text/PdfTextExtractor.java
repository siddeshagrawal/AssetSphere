package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.IOException;
import java.io.InputStream;
import org.apache.pdfbox.io.MemoryUsageSetting;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(10)
class PdfTextExtractor implements TextExtractor {

    @Override
    public boolean supports(AssetProcessingInput input) {
        return "application/pdf".equalsIgnoreCase(input.mimeType());
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        try (PDDocument document = PDDocument.load(content, MemoryUsageSetting.setupMixed(8 * 1024 * 1024))) {
            return ExtractionResult.extracted(new PDFTextStripper().getText(document), "PDFBOX");
        } catch (IOException exception) {
            throw new TextExtractionException("PDF text extraction failed", exception);
        }
    }
}
