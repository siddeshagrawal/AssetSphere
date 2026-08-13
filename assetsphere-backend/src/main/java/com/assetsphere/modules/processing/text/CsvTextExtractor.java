package com.assetsphere.modules.processing.text;

import com.assetsphere.modules.asset.api.AssetProcessingInput;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(32)
class CsvTextExtractor implements TextExtractor {

    private static final int MAX_ROWS = 10_000;
    private static final int MAX_COLUMNS = 256;
    private static final int MAX_CELL_CHARACTERS = 4_000;
    private static final int MAX_OUTPUT_CHARACTERS = 1_000_000;

    @Override
    public boolean supports(AssetProcessingInput input) {
        return "text/csv".equalsIgnoreCase(input.mimeType())
                || input.originalFilename().toLowerCase(Locale.ROOT).endsWith(".csv");
    }

    @Override
    public ExtractionResult extract(AssetProcessingInput input, InputStream content) {
        String csv = SafeTextDecoder.decode(content, "CSV");
        try {
            return ExtractionResult.extracted(readRows(csv), "CSV");
        } catch (IllegalArgumentException exception) {
            throw new TextExtractionException("CSV text extraction failed", exception);
        }
    }

    private String readRows(String csv) {
        List<String> headers = null;
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        StringBuilder output = new StringBuilder();
        boolean quoted = false;
        int rows = 0;
        for (int index = 0; index <= csv.length() && rows < MAX_ROWS && output.length() < MAX_OUTPUT_CHARACTERS; index++) {
            char current = index == csv.length() ? '\n' : csv.charAt(index);
            if (current == '"') {
                if (quoted && index + 1 < csv.length() && csv.charAt(index + 1) == '"') {
                    appendCellCharacter(cell, '"');
                    index++;
                } else if (quoted || cell.isEmpty()) {
                    quoted = !quoted;
                } else {
                    throw new IllegalArgumentException("Unescaped quote in CSV cell");
                }
            } else if (!quoted && (current == ',' || current == '\n' || current == '\r')) {
                if (row.size() < MAX_COLUMNS) row.add(cell.toString().strip());
                cell.setLength(0);
                if (current == '\n' || current == '\r') {
                    if (current == '\r' && index + 1 < csv.length() && csv.charAt(index + 1) == '\n') index++;
                    if (headers == null) headers = List.copyOf(row);
                    else appendRow(output, headers, row, rows);
                    row.clear();
                    rows++;
                }
            } else {
                appendCellCharacter(cell, current);
            }
        }
        if (quoted) throw new IllegalArgumentException("Unterminated quoted CSV cell");
        return output.toString().strip();
    }

    private void appendCellCharacter(StringBuilder cell, char value) {
        if (cell.length() < MAX_CELL_CHARACTERS) cell.append(value);
    }

    private void appendRow(StringBuilder output, List<String> headers, List<String> row, int rowNumber) {
        if (row.stream().allMatch(String::isBlank)) return;
        append(output, "Row " + rowNumber + ": ");
        for (int column = 0; column < row.size() && output.length() < MAX_OUTPUT_CHARACTERS; column++) {
            if (column > 0) append(output, " | ");
            String header = column < headers.size() && !headers.get(column).isBlank()
                    ? headers.get(column) : "Column " + (column + 1);
            append(output, header + ": " + row.get(column));
        }
        append(output, "\n");
    }

    private void append(StringBuilder output, String value) {
        int remaining = MAX_OUTPUT_CHARACTERS - output.length();
        if (remaining > 0) output.append(value, 0, Math.min(value.length(), remaining));
    }
}
