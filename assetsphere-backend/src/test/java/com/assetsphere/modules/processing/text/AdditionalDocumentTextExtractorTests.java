package com.assetsphere.modules.processing.text;

import static org.assertj.core.api.Assertions.assertThat;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class AdditionalDocumentTextExtractorTests {

    @Test
    void extractsUtf8TextAndMarkdownStructure() {
        var text = new PlainTextExtractor().extract(input("notes.txt", "text/plain"), bytes("First\nSecond"));
        var markdown = new MarkdownTextExtractor().extract(input("notes.md", "text/markdown"), bytes("# Heading\n- Detail"));

        assertThat(text.text()).contains("First\nSecond");
        assertThat(markdown.text()).contains("# Heading", "- Detail");
    }

    @Test
    void extractsCsvUsingHeaderNames() {
        var result = new CsvTextExtractor().extract(input("people.csv", "text/csv"), bytes("name,role\nAda,Engineer\n"));

        assertThat(result.text()).contains("Row 1", "name: Ada", "role: Engineer");
    }

    @Test
    void extractsSchemaFreeJsonFieldsAndValues() {
        var result = new JsonTextExtractor(new ObjectMapper()).extract(
                input("data.json", "application/json"), bytes("{\"team\":{\"name\":\"Platform\"},\"active\":true}"));

        assertThat(result.text()).contains("name: Platform", "active: true");
    }

    @Test
    void extractsXlsxSheetCellsWithoutEvaluatingFormulas() throws Exception {
        byte[] workbook;
        try (XSSFWorkbook document = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = document.createSheet("Roadmap").createRow(0);
            row.createCell(0).setCellValue("Milestone");
            row.createCell(1).setCellFormula("1+1");
            document.write(output);
            workbook = output.toByteArray();
        }

        var result = new XlsxTextExtractor().extract(input("roadmap.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"), new ByteArrayInputStream(workbook));

        assertThat(result.text()).contains("Sheet: Roadmap", "Milestone", "Formula: 1+1");
    }

    @Test
    void extractsPptxSlideNumberAndText() throws Exception {
        byte[] presentation;
        try (XMLSlideShow document = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createSlide().createTextBox().setText("Quarterly direction");
            document.write(output);
            presentation = output.toByteArray();
        }

        var result = new PptxTextExtractor().extract(input("direction.pptx",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation"), new ByteArrayInputStream(presentation));

        assertThat(result.text()).contains("Slide 1", "Quarterly direction");
    }

    private ByteArrayInputStream bytes(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }

    private AssetProcessingInput input(String filename, String mimeType) {
        return new AssetProcessingInput(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "object", "name", null,
                filename, mimeType, 10);
    }
}
