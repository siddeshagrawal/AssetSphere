package com.assetsphere.modules.processing.text;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.UUID;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class DocxTextExtractorTests {

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    void extractsParagraphAndTableText() throws Exception {
        ExtractionResult result = extractor.extract(docxInput(), new ByteArrayInputStream(document("A distinctive DOCX phrase", "Table evidence")));

        assertThat(result.status()).isEqualTo(ExtractionStatus.EXTRACTED);
        assertThat(result.text()).contains("A distinctive DOCX phrase", "Table evidence");
        assertThat(result.extractorType()).isEqualTo("APACHE_POI");
    }

    @Test
    void returnsNoTextForEmptyDocument() throws Exception {
        ExtractionResult result = extractor.extract(docxInput(), new ByteArrayInputStream(document(null, null)));

        assertThat(result.status()).isEqualTo(ExtractionStatus.NO_TEXT_EXTRACTED);
        assertThat(result.text()).isBlank();
    }

    @Test
    void rejectsCorruptDocument() {
        assertThatThrownBy(() -> extractor.extract(docxInput(), new ByteArrayInputStream("not-a-docx".getBytes())))
                .isInstanceOf(TextExtractionException.class);
    }

    private AssetProcessingInput docxInput() {
        return new AssetProcessingInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "object", "name", null,
                "source.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 10);
    }

    private byte[] document(String paragraph, String cell) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (paragraph != null) {
                document.createParagraph().createRun().setText(paragraph);
            }
            if (cell != null) {
                document.createTable(1, 1).getRow(0).getCell(0).setText(cell);
            }
            document.write(output);
            return output.toByteArray();
        }
    }
}
