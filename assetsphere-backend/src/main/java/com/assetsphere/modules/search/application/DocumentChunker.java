package com.assetsphere.modules.search.application;

import com.assetsphere.modules.common.exception.BusinessRuleViolationException;
import com.assetsphere.modules.search.api.SemanticIndexProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class DocumentChunker {

    private final SemanticIndexProperties properties;

    List<String> chunk(String text) {
        if (text == null || text.isBlank()) return List.of();
        int size = properties.getChunkSize();
        int overlap = properties.getChunkOverlap();
        if (size < 100 || overlap < 0 || overlap >= size) throw new BusinessRuleViolationException("Semantic chunking configuration is invalid");
        List<String> paragraphs = List.of(text.strip().split("\\R{2,}"));
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String paragraph : paragraphs) {
            String normalized = paragraph.strip();
            if (normalized.isBlank()) continue;
            appendParagraph(chunks, current, normalized, size, overlap);
            if (chunks.size() >= properties.getMaxChunksPerDocument()) break;
        }
        if (!current.isEmpty() && chunks.size() < properties.getMaxChunksPerDocument()) chunks.add(current.toString());
        return List.copyOf(chunks);
    }

    private void appendParagraph(List<String> chunks, StringBuilder current, String paragraph, int size, int overlap) {
        String remaining = paragraph;
        while (!remaining.isEmpty()) {
            int available = size - current.length() - (current.isEmpty() ? 0 : 2);
            if (available <= 0) {
                flush(chunks, current, overlap);
                available = size - current.length();
            }
            if (remaining.length() <= available) {
                if (!current.isEmpty()) current.append("\n\n");
                current.append(remaining);
                return;
            }
            int boundary = remaining.lastIndexOf(' ', available);
            if (boundary < Math.max(1, available / 2)) boundary = available;
            if (!current.isEmpty()) current.append("\n\n");
            current.append(remaining, 0, boundary).trimToSize();
            flush(chunks, current, overlap);
            remaining = remaining.substring(boundary).stripLeading();
        }
    }

    private void flush(List<String> chunks, StringBuilder current, int overlap) {
        String value = current.toString().strip();
        if (!value.isEmpty() && chunks.size() < properties.getMaxChunksPerDocument()) chunks.add(value);
        String tail = value.length() <= overlap ? value : value.substring(value.length() - overlap);
        current.setLength(0);
        current.append(tail);
    }
}
