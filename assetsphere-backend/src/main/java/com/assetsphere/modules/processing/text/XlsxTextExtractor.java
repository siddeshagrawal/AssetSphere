package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.util.Locale;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(34)
class XlsxTextExtractor implements TextExtractor {

    private static final String MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    private static final int MAX_SHEETS = 100;
    private static final int MAX_ROWS = 10_000;
    private static final int MAX_CELLS_PER_ROW = 256;
    private static final int MAX_CELL_CHARACTERS = 4_000;
    private static final int MAX_OUTPUT_CHARACTERS = 1_000_000;

    @Override
    public boolean supports(AssetProcessingInput input) {
        return MIME.equalsIgnoreCase(input.mimeType())
                || input.originalFilename().toLowerCase(Locale.ROOT).endsWith(".xlsx");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        try (Workbook workbook = WorkbookFactory.create(content)) {
            DataFormatter formatter = new DataFormatter(Locale.ROOT);
            StringBuilder output = new StringBuilder();
            int rowCount = 0;
            for (int sheetIndex = 0; sheetIndex < Math.min(workbook.getNumberOfSheets(), MAX_SHEETS)
                    && rowCount < MAX_ROWS && output.length() < MAX_OUTPUT_CHARACTERS; sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                append(output, "Sheet: " + sheet.getSheetName() + "\n");
                for (Row row : sheet) {
                    if (rowCount++ >= MAX_ROWS || output.length() >= MAX_OUTPUT_CHARACTERS) break;
                    StringBuilder rowText = new StringBuilder();
                    int cells = 0;
                    for (Cell cell : row) {
                        if (cells++ >= MAX_CELLS_PER_ROW) break;
                        String value = cell.getCellType() == CellType.FORMULA
                                ? "Formula: " + cell.getCellFormula() : formatter.formatCellValue(cell);
                        if (value.isBlank()) continue;
                        if (!rowText.isEmpty()) rowText.append(" | ");
                        rowText.append(value, 0, Math.min(value.length(), MAX_CELL_CHARACTERS));
                    }
                    if (!rowText.isEmpty()) append(output, "Row " + (row.getRowNum() + 1) + ": " + rowText + "\n");
                }
            }
            return ExtractionResult.extracted(output.toString().strip(), "APACHE_POI_XLSX");
        } catch (Exception exception) {
            throw new TextExtractionException("XLSX text extraction failed", exception);
        }
    }

    private void append(StringBuilder output, String value) {
        int remaining = MAX_OUTPUT_CHARACTERS - output.length();
        if (remaining > 0) output.append(value, 0, Math.min(value.length(), remaining));
    }
}
