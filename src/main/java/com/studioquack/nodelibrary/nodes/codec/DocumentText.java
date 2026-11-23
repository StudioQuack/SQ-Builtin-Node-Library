package com.studioquack.nodelibrary.nodes.codec;

import java.util.List;
import java.util.Locale;

/**
 * In-JVM plain-text extraction for binary document attachments (XLSX / ODS / PDF),
 * exposed to the LLM content pipeline ({@code OpenAiContentParts}) so a document the
 * target chat server cannot consume natively still reaches the model as text instead
 * of a drop notice. Formats without an in-JVM extractor (docx, pptx, legacy OLE2, …)
 * return {@code null} and the caller keeps its degradation path (Docling notice).
 *
 * <p>Pure and bounded: no network, no logs, unzip/XML hardening lives in
 * {@link SpreadsheetXml} (XXE-disabled, zip-bomb caps) and {@link PdfText} (stream
 * caps). Rendered text is capped at {@link #MAX_RENDER_CHARS} — the caller applies
 * its own (smaller) prompt cap on top.
 */
public final class DocumentText {

    /** Decoded-bytes ceiling for in-JVM extraction (matches FilesSupport's 8 MiB file cap). */
    public static final long MAX_EXTRACT_BYTES = 8L * 1024 * 1024;

    /** Rendered-text ceiling; the LLM prompt path caps further (64k) on top of this. */
    static final int MAX_RENDER_CHARS = 256 * 1024;

    private DocumentText() {
        // utility — never instantiated
    }

    /** True when mime/filename names a binary format with an in-JVM extractor here. */
    public static boolean isExtractable(String mime, String filename) {
        return kind(mime, filename) != null;
    }

    /**
     * Extract plain text for the document; {@code null} when the format is unsupported,
     * the payload is unreadable, exceeds {@link #MAX_EXTRACT_BYTES}, or yields no text
     * (e.g. an image-only PDF) — the caller then falls back to its degradation notice.
     */
    public static String extract(String mime, String filename, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_EXTRACT_BYTES) {
            return null;
        }
        String kind = kind(mime, filename);
        if (kind == null) {
            return null;
        }
        try {
            return switch (kind) {
                case "xlsx" -> spreadsheetText(SpreadsheetXml.readXlsx(bytes));
                case "ods" -> spreadsheetText(SpreadsheetXml.readOds(bytes));
                case "pdf" -> blankToNull(PdfText.extract(bytes));
                default -> null;
            };
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            // Untrusted upload — a malformed container must degrade, never fail the turn.
            return null;
        }
    }

    /** Format discriminator from MIME first, filename extension as fallback. */
    private static String kind(String mime, String filename) {
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (m.endsWith("spreadsheetml.sheet") || n.endsWith(".xlsx") || n.endsWith(".xlsm")) {
            return "xlsx";
        }
        if (m.endsWith("opendocument.spreadsheet") || n.endsWith(".ods")) {
            return "ods";
        }
        if ("application/pdf".equals(m) || n.endsWith(".pdf")) {
            return "pdf";
        }
        return null;
    }

    /** Sheets as {@code [Sheet: name]} + tab-joined rows, capped at {@link #MAX_RENDER_CHARS}. */
    private static String spreadsheetText(List<SpreadsheetXml.Sheet> sheets) {
        if (sheets == null || sheets.isEmpty()) {
            return null;
        }
        StringBuilder out = new StringBuilder();
        for (SpreadsheetXml.Sheet sheet : sheets) {
            if (out.length() >= MAX_RENDER_CHARS) {
                break;
            }
            out.append("[Sheet: ").append(sheet.name()).append("]\n");
            for (List<String> row : sheet.rows()) {
                out.append(String.join("\t", row)).append('\n');
                if (out.length() >= MAX_RENDER_CHARS) {
                    out.append("[…truncated at ").append(MAX_RENDER_CHARS).append(" chars]\n");
                    break;
                }
            }
        }
        return blankToNull(out.toString());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
