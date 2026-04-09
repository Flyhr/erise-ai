package com.erise.ai.backend.modules;

import com.erise.ai.backend.common.util.TextContentUtils;
import com.erise.ai.backend.integration.ai.CloudAiClient;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;

@Service
class StoredTextExtractionSupport {

    private static final int REPEATED_BOUNDARY_SCAN_LINES = 2;
    private static final double REPEATED_LINE_RATIO = 0.6D;
    private static final int MIN_MEANINGFUL_PDF_TEXT_LENGTH = 80;

    private final RagKnowledgeService ragKnowledgeService;
    private final CloudAiClient cloudAiClient;
    private final Tika tika;

    StoredTextExtractionSupport(RagKnowledgeService ragKnowledgeService, CloudAiClient cloudAiClient) {
        this.ragKnowledgeService = ragKnowledgeService;
        this.cloudAiClient = cloudAiClient;
        this.tika = new Tika();
        this.tika.setMaxStringLength(-1);
    }

    String extractPlainText(Long ownerUserId, String fileName, String fileExt, InputStream stream) throws IOException {
        return switch (normalizeExtension(fileExt)) {
            case "txt" -> TextContentUtils.decodeText(stream.readAllBytes());
            case "md", "markdown" -> stripMarkdown(TextContentUtils.decodeText(stream.readAllBytes()));
            case "pdf" -> extractPdf(ownerUserId, fileName, stream).plainText();
            case "doc" -> extractDocText(stream);
            case "docx" -> extractDocxText(stream);
            default -> "";
        };
    }

    List<RagKnowledgeService.ChunkInput> extractChunks(Long ownerUserId, String fileName, String fileExt, InputStream stream) throws IOException {
        return switch (normalizeExtension(fileExt)) {
            case "txt" -> ragKnowledgeService.splitText(TextContentUtils.decodeText(stream.readAllBytes()), null);
            case "md", "markdown" -> ragKnowledgeService.splitText(stripMarkdown(TextContentUtils.decodeText(stream.readAllBytes())), null);
            case "pdf" -> extractPdf(ownerUserId, fileName, stream).chunks();
            case "doc" -> extractDocChunks(stream);
            case "docx" -> extractDocxChunks(stream);
            default -> List.of();
        };
    }

    private String normalizeExtension(String fileExt) {
        return fileExt == null ? "" : fileExt.toLowerCase(Locale.ROOT);
    }

    private PdfExtractionResult extractPdf(Long ownerUserId, String fileName, InputStream stream) throws IOException {
        byte[] bytes = stream.readAllBytes();
        PdfExtractionResult pdfBoxResult = attemptPdfExtraction(() -> extractPdfWithPdfBox(bytes));
        if (hasMeaningfulPdfText(pdfBoxResult)) {
            return pdfBoxResult;
        }

        PdfExtractionResult tikaResult = attemptPdfExtraction(() -> extractPdfWithTika(bytes));
        if (hasMeaningfulPdfText(tikaResult)) {
            return tikaResult;
        }

        if (ownerUserId == null) {
            return betterOf(pdfBoxResult, tikaResult);
        }

        CloudAiClient.PdfOcrResponse ocrResponse = cloudAiClient.extractPdfText(
                ownerUserId,
                resolvePdfFileName(fileName),
                bytes,
                UUID.randomUUID().toString()
        );
        return buildPdfOcrResult(ocrResponse);
    }

    private PdfExtractionResult extractPdfWithPdfBox(byte[] bytes) throws IOException {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            List<String> rawPageTexts = new ArrayList<>();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                rawPageTexts.add(stripper.getText(document));
            }
            return buildPdfResult(rawPageTexts, null);
        }
    }

    private PdfExtractionResult extractPdfWithTika(byte[] bytes) throws IOException {
        try {
            String text = tika.parseToString(new ByteArrayInputStream(bytes));
            return buildPdfResult(splitTikaPages(text), text);
        } catch (TikaException exception) {
            throw new IOException("Tika PDF extraction failed", exception);
        }
    }

    private PdfExtractionResult buildPdfOcrResult(CloudAiClient.PdfOcrResponse response) {
        List<String> pageTexts = response == null || response.pageTexts() == null
                ? List.of()
                : response.pageTexts();
        return buildPdfResult(pageTexts, response == null ? "" : response.text());
    }

    private PdfExtractionResult buildPdfResult(List<String> rawPageTexts, String fallbackPlainText) {
        List<String> cleanedPages = cleanPdfPages(rawPageTexts);
        List<RagKnowledgeService.ChunkInput> chunks = new ArrayList<>();
        for (int index = 0; index < cleanedPages.size(); index++) {
            String pageText = cleanedPages.get(index);
            if (pageText.isBlank()) {
                continue;
            }
            chunks.addAll(ragKnowledgeService.splitText(pageText, index + 1));
        }

        String plainText = cleanedPages.stream()
                .filter(page -> !page.isBlank())
                .collect(Collectors.joining("\n\n"));
        if (plainText.isBlank()) {
            plainText = normalizePlainText(fallbackPlainText);
        }
        if (chunks.isEmpty() && !plainText.isBlank()) {
            chunks = ragKnowledgeService.splitText(plainText, null);
        }
        return new PdfExtractionResult(plainText, cleanedPages, chunks);
    }

    private List<String> splitTikaPages(String tikaText) {
        if (tikaText == null || tikaText.isBlank()) {
            return List.of();
        }
        String[] rawPages = tikaText.split("\\f");
        List<String> pages = new ArrayList<>();
        for (String rawPage : rawPages) {
            String normalized = normalizePlainText(rawPage);
            if (!normalized.isBlank()) {
                pages.add(normalized);
            }
        }
        if (!pages.isEmpty()) {
            return pages;
        }
        return List.of(normalizePlainText(tikaText));
    }

    private List<String> cleanPdfPages(List<String> rawPageTexts) {
        if (rawPageTexts == null || rawPageTexts.isEmpty()) {
            return List.of();
        }
        List<List<String>> pageLines = rawPageTexts.stream()
                .map(this::normalizePdfLines)
                .toList();
        Set<String> repeatedHeaders = detectRepeatedBoundaryLines(pageLines, true);
        Set<String> repeatedFooters = detectRepeatedBoundaryLines(pageLines, false);

        List<String> cleanedPages = new ArrayList<>();
        for (List<String> lines : pageLines) {
            List<String> filtered = new ArrayList<>();
            for (int index = 0; index < lines.size(); index++) {
                String line = lines.get(index);
                if (shouldDropPdfLine(line, index, lines.size(), repeatedHeaders, repeatedFooters)) {
                    continue;
                }
                filtered.add(line);
            }
            cleanedPages.add(joinPdfLines(filtered));
        }
        return cleanedPages;
    }

    private Set<String> detectRepeatedBoundaryLines(List<List<String>> pageLines, boolean header) {
        long populatedPages = pageLines.stream().filter(lines -> !lines.isEmpty()).count();
        if (populatedPages < 2) {
            return Set.of();
        }
        int threshold = Math.max(2, (int) Math.ceil(populatedPages * REPEATED_LINE_RATIO));
        Map<String, Integer> counts = new HashMap<>();
        for (List<String> lines : pageLines) {
            if (lines.isEmpty()) {
                continue;
            }
            int start = header ? 0 : Math.max(0, lines.size() - REPEATED_BOUNDARY_SCAN_LINES);
            int end = header ? Math.min(lines.size(), REPEATED_BOUNDARY_SCAN_LINES) : lines.size();
            Set<String> uniqueCandidates = new LinkedHashSet<>();
            for (int index = start; index < end; index++) {
                String candidate = lines.get(index);
                if (candidate.length() > 80 || isStandalonePageNumber(candidate)) {
                    continue;
                }
                uniqueCandidates.add(candidate);
            }
            for (String candidate : uniqueCandidates) {
                counts.merge(candidate, 1, Integer::sum);
            }
        }
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() >= threshold)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean shouldDropPdfLine(String line,
                                      int index,
                                      int totalLines,
                                      Set<String> repeatedHeaders,
                                      Set<String> repeatedFooters) {
        if (line == null || line.isBlank()) {
            return true;
        }
        if (isStandalonePageNumber(line)) {
            return true;
        }
        if (index < REPEATED_BOUNDARY_SCAN_LINES && repeatedHeaders.contains(line)) {
            return true;
        }
        return index >= Math.max(0, totalLines - REPEATED_BOUNDARY_SCAN_LINES) && repeatedFooters.contains(line);
    }

    private List<String> normalizePdfLines(String rawPageText) {
        if (rawPageText == null || rawPageText.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String rawLine : rawPageText.replace('\u0000', ' ').split("\\R")) {
            String normalized = normalizeTextLine(rawLine);
            if (!normalized.isBlank()) {
                lines.add(normalized);
            }
        }
        return lines;
    }

    private String joinPdfLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) {
            return "";
        }
        return normalizePlainText(String.join("\n", lines));
    }

    private String normalizeTextLine(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace('\u00A0', ' ')
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String normalizePlainText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("\r", "")
                .replace('\u00A0', ' ')
                .replaceAll("[ \\t\\x0B\\f]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean isStandalonePageNumber(String line) {
        String normalized = normalizeTextLine(line).toLowerCase(Locale.ROOT);
        return normalized.matches("^第\\s*\\d+\\s*页(?:\\s*/\\s*第?\\s*\\d+\\s*页)?$")
                || normalized.matches("^page\\s*\\d+(?:\\s*of\\s*\\d+)?$")
                || normalized.matches("^[\\-–—]?\\s*\\d+\\s*[\\-–—]?$")
                || normalized.matches("^\\d+\\s*/\\s*\\d+$");
    }

    private PdfExtractionResult attemptPdfExtraction(PdfExtractor extractor) {
        try {
            return extractor.extract();
        } catch (Exception ignored) {
            return PdfExtractionResult.empty();
        }
    }

    private boolean hasMeaningfulPdfText(PdfExtractionResult result) {
        if (result == null) {
            return false;
        }
        if (result.plainText() != null && result.plainText().length() >= MIN_MEANINGFUL_PDF_TEXT_LENGTH) {
            return true;
        }
        long populatedPages = result.pageTexts().stream().filter(page -> page != null && !page.isBlank()).count();
        return populatedPages >= 2 || result.chunks().size() >= 2;
    }

    private PdfExtractionResult betterOf(PdfExtractionResult left, PdfExtractionResult right) {
        int leftLength = left == null || left.plainText() == null ? 0 : left.plainText().length();
        int rightLength = right == null || right.plainText() == null ? 0 : right.plainText().length();
        return rightLength > leftLength ? right : left;
    }

    private String resolvePdfFileName(String fileName) {
        if (fileName != null && !fileName.isBlank()) {
            return fileName;
        }
        return "upload.pdf";
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

    @FunctionalInterface
    private interface PdfExtractor {
        PdfExtractionResult extract() throws Exception;
    }

    private record PdfExtractionResult(String plainText,
                                       List<String> pageTexts,
                                       List<RagKnowledgeService.ChunkInput> chunks) {

        private static PdfExtractionResult empty() {
            return new PdfExtractionResult("", List.of(), List.of());
        }
    }
}
