package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.util.TextContentUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.BodyElementType;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

@Service
class StoredTextExtractionSupport {

    private final RagKnowledgeService ragKnowledgeService;

    StoredTextExtractionSupport(RagKnowledgeService ragKnowledgeService) {
        this.ragKnowledgeService = ragKnowledgeService;
    }

    String extractPlainText(String fileExt, InputStream stream) throws IOException {
        return switch (normalizeExtension(fileExt)) {
            case "txt" -> TextContentUtils.decodeText(stream.readAllBytes());
            case "md", "markdown" -> stripMarkdown(TextContentUtils.decodeText(stream.readAllBytes()));
            case "pdf" -> extractPdfText(stream);
            case "doc" -> extractDocText(stream);
            case "docx" -> extractDocxText(stream);
            default -> "";
        };
    }

    List<RagKnowledgeService.ChunkInput> extractChunks(String fileExt, InputStream stream) throws IOException {
        return switch (normalizeExtension(fileExt)) {
            case "txt" -> ragKnowledgeService.splitText(TextContentUtils.decodeText(stream.readAllBytes()), null);
            case "md", "markdown" -> ragKnowledgeService.splitText(stripMarkdown(TextContentUtils.decodeText(stream.readAllBytes())), null);
            case "pdf" -> extractPdfChunks(stream);
            case "doc" -> extractDocChunks(stream);
            case "docx" -> extractDocxChunks(stream);
            default -> List.of();
        };
    }

    private String normalizeExtension(String fileExt) {
        return fileExt == null ? "" : fileExt.toLowerCase(Locale.ROOT);
    }

    private List<RagKnowledgeService.ChunkInput> extractPdfChunks(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                chunks.addAll(ragKnowledgeService.splitText(stripper.getText(document), page));
            }
            return chunks;
        }
    }

    private List<RagKnowledgeService.ChunkInput> extractDocChunks(InputStream stream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(stream); WordExtractor extractor = new WordExtractor(document)) {
            return ragKnowledgeService.splitText(extractor.getText(), null);
        }
    }

    private List<RagKnowledgeService.ChunkInput> extractDocxChunks(InputStream stream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(stream)) {
            List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
            for (String block : readDocxBlocks(document)) {
                chunks.addAll(ragKnowledgeService.splitText(block, null));
            }
            return chunks;
        }
    }

    private String extractPdfText(InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            StringBuilder content = new StringBuilder();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                String text = stripper.getText(document).trim();
                if (!text.isBlank()) {
                    if (!content.isEmpty()) {
                        content.append("\n\n");
                    }
                    content.append(text);
                }
            }
            return content.toString();
        }
    }

    private String extractDocText(InputStream stream) throws IOException {
        try (HWPFDocument document = new HWPFDocument(stream); WordExtractor extractor = new WordExtractor(document)) {
            return extractor.getText();
        }
    }

    private String extractDocxText(InputStream stream) throws IOException {
        try (XWPFDocument document = new XWPFDocument(stream)) {
            return String.join("\n\n", readDocxBlocks(document));
        }
    }

    private List<String> readDocxBlocks(XWPFDocument document) {
        List<String> blocks = new ArrayList<>();
        for (IBodyElement element : document.getBodyElements()) {
            if (element.getElementType() == BodyElementType.PARAGRAPH) {
                String text = paragraphPlainText((XWPFParagraph) element);
                if (!text.isBlank()) {
                    blocks.add(text);
                }
                continue;
            }
            if (element.getElementType() == BodyElementType.TABLE) {
                XWPFTable table = (XWPFTable) element;
                for (XWPFTableRow row : table.getRows()) {
                    List<String> cells = new ArrayList<>();
                    for (XWPFTableCell cell : row.getTableCells()) {
                        String cellText = cell.getText() == null ? "" : cell.getText().replace("\n", " ").trim();
                        if (!cellText.isBlank()) {
                            cells.add(cellText);
                        }
                    }
                    if (!cells.isEmpty()) {
                        blocks.add(String.join(" | ", cells));
                    }
                }
            }
        }
        return blocks;
    }

    private String paragraphPlainText(XWPFParagraph paragraph) {
        String text = paragraph.getText() == null ? "" : paragraph.getText().trim();
        if (text.isBlank()) {
            return "";
        }
        return paragraph.getNumID() == null ? text : "- " + text;
    }

    private String stripMarkdown(String markdown) {
        return markdown
                .replaceAll("```[\\s\\S]*?```", " ")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[[^\\]]*]\\([^)]*\\)", " ")
                .replaceAll("[#>*`_\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
