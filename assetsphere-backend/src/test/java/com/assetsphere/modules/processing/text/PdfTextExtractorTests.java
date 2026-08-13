package com.assetsphere.modules.processing.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;

class PdfTextExtractorTests {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    void extractsTextFromTextBasedPdf() throws Exception {
        ExtractionResult result = extractor.extract(pdfInput(), new ByteArrayInputStream(pdf("AssetSphere PDF evidence")));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).contains("AssetSphere PDF evidence");
        assertThat(result.extractorType()).isEqualTo("PDFBOX");
    }

    @Test
    void returnsNoTextForEmptyPdf() throws Exception {
        ExtractionResult result = extractor.extract(pdfInput(), new ByteArrayInputStream(pdf(null)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.NO_TEXT_EXTRACTED);
        assertThat(result.text()).isBlank();
    }

    @Test
    void rejectsCorruptPdf() {
        assertThatThrownBy(() -> extractor.extract(pdfInput(), new ByteArrayInputStream("not-a-pdf".getBytes())))
                .isInstanceOf(TextExtractionException.class);
    }

    private AssetProcessingInput pdfInput() {
        return input("application/pdf");
    }

    private AssetProcessingInput input(String mimeType) {
        return new AssetProcessingInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "object", "name", null,
                "source.pdf", mimeType, 10);
    }

    private byte[] pdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(PDType1Font.HELVETICA, 12);
                    stream.newLineAtOffset(72, 720);
                    stream.showText(text);
                    stream.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
