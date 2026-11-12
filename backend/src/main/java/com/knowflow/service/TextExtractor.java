package com.knowflow.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class TextExtractor {

    public String extract(Path file, String originalFilename) throws IOException {
        String extension = extensionOf(originalFilename);
        return switch (extension) {
            case ".txt" -> Files.readString(file, StandardCharsets.UTF_8);
            case ".pdf" -> extractPdf(file);
            case ".docx" -> extractDocx(file);
            default -> throw new IOException("不支持的文件类型: " + extension);
        };
    }

    private String extractPdf(Path file) throws IOException {
        try (PDDocument document = PDDocument.load(file.toFile())) {
            return new PDFTextStripper().getText(document);
        }
    }

    private String extractDocx(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file);
             XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return "";
        }
        return filename.substring(dot).toLowerCase(Locale.ROOT);
    }
}
