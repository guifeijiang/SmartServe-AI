package com.knowflow.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TextExtractorTest {

    private final TextExtractor textExtractor = new TextExtractor();

    @TempDir
    Path tempDir;

    @Test
    void extractsUtf8Txt() throws Exception {
        Path file = tempDir.resolve("note.txt");
        Files.writeString(file, "你好 TXT", StandardCharsets.UTF_8);

        String text = textExtractor.extract(file, "note.txt");

        assertTrue(text.contains("你好 TXT"));
    }

    @Test
    void extractsPdf() throws Exception {
        Path file = tempDir.resolve("note.pdf");
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                stream.beginText();
                stream.setFont(PDType1Font.HELVETICA, 12);
                stream.newLineAtOffset(50, 700);
                stream.showText("Hello PDF KnowFlow");
                stream.endText();
            }
            document.save(file.toFile());
        }

        String text = textExtractor.extract(file, "note.pdf");

        assertTrue(text.contains("Hello PDF KnowFlow"));
    }

    @Test
    void extractsDocx() throws Exception {
        Path file = tempDir.resolve("note.docx");
        try (XWPFDocument document = new XWPFDocument()) {
            XWPFParagraph paragraph = document.createParagraph();
            XWPFRun run = paragraph.createRun();
            run.setText("Hello DOCX KnowFlow");
            try (var output = Files.newOutputStream(file)) {
                document.write(output);
            }
        }

        String text = textExtractor.extract(file, "note.docx");

        assertTrue(text.contains("Hello DOCX KnowFlow"));
    }
}
