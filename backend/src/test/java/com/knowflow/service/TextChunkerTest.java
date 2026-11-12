package com.knowflow.service;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    private final TextChunker textChunker = new TextChunker();

    @Test
    void splitsLongTextWithOverlap() {
        String text = "A".repeat(700);
        List<String> chunks = textChunker.split(text);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).length());
        String overlap = chunks.get(0).substring(chunks.get(0).length() - 80);
        assertTrue(chunks.get(1).startsWith(overlap));
    }

    @Test
    void prefersParagraphBreaks() {
        String paragraph1 = "段一".repeat(200);
        String paragraph2 = "段二".repeat(200);
        List<String> chunks = textChunker.split(paragraph1 + "\n\n" + paragraph2);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).contains("段一"));
        assertTrue(chunks.get(1).contains("段二"));
    }
}
