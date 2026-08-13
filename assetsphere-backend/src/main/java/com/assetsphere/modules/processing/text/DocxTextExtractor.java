package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.IOException;
import java.io.InputStream;
import java.util.stream.Collectors;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
class DocxTextExtractor implements TextExtractor {

    private static final String DOCX_MIME = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";

    @Override
    public boolean supports(AssetProcessingInput input) {
        return DOCX_MIME.equalsIgnoreCase(input.mimeType());
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        try (XWPFDocument document = new XWPFDocument(content)) {
            String paragraphs = document.getParagraphs().stream()
                    .map(paragraph -> paragraph.getText().trim())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            String tables = document.getTables().stream()
                    .flatMap(table -> table.getRows().stream())
                    .flatMap(row -> row.getTableCells().stream())
                    .map(cell -> cell.getText().trim())
                    .filter(text -> !text.isBlank())
                    .collect(Collectors.joining("\n"));
            return ExtractionResult.extracted(String.join("\n", paragraphs, tables).trim(), "APACHE_POI");
        } catch (Exception exception) {
            throw new TextExtractionException("DOCX text extraction failed", exception);
        }
    }
}
