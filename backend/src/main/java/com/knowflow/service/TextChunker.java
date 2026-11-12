package com.knowflow.service;

import java.util.ArrayList;
import java.util.List;

/**
 * 按约 500 字切块，块之间重叠 80 字；优先在段落或换行处断开，避免把一段从中间硬切。
 */
public class TextChunker {

    static final int CHUNK_SIZE = 500;
    static final int OVERLAP = 80;

    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").trim();
        List<String> chunks = new ArrayList<>();
        int start = 0;
        int length = normalized.length();
        while (start < length) {
            int greedyEnd = Math.min(start + CHUNK_SIZE, length);
            int end = chooseBreak(normalized, start, greedyEnd, length);
            String chunk = normalized.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= length) {
                break;
            }
            int nextStart = end - OVERLAP;
            if (nextStart <= start) {
                nextStart = end;
            }
            start = nextStart;
        }
        return chunks;
    }

    private int chooseBreak(String text, int start, int greedyEnd, int length) {
        if (greedyEnd >= length) {
            return length;
        }
        int paragraph = text.lastIndexOf("\n\n", greedyEnd - 1);
        if (paragraph > start) {
            return paragraph;
        }
        int line = text.lastIndexOf('\n', greedyEnd - 1);
        if (line > start) {
            return line;
        }
        return greedyEnd;
    }
}
