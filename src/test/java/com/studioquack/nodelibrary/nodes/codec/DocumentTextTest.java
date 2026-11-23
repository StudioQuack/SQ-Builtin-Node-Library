package com.studioquack.nodelibrary.nodes.codec;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DocumentText: in-JVM plain-text extraction for LLM-bound binary documents.
 * XLSX/ODS/PDF extract; unsupported formats, corrupt payloads and oversize
 * inputs return null so the caller degrades to its notice — never throws.
 */
public class DocumentTextTest {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    @Test
    void xlsxExtractsSheetNamesAndCellText() throws Exception {
        String text = DocumentText.extract(XLSX_MIME, "book.xlsx", minimalXlsx());
        assertNotNull(text);
        assertTrue(text.contains("[Sheet:"), text);
        assertTrue(text.contains("name\tage"), text);
        assertTrue(text.contains("Ada\t36"), text);
    }

    @Test
    void pdfExtractsTextFromPlainContentStream() {
        String text = DocumentText.extract("application/pdf", "spec.pdf", minimalPdf());
        assertNotNull(text);
        assertTrue(text.contains("Hello PDF text"), text);
    }

    @Test
    void filenameExtensionIsTheFallbackWhenMimeIsGeneric() throws Exception {
        String text = DocumentText.extract("application/octet-stream", "book.xlsx", minimalXlsx());
        assertNotNull(text);
        assertTrue(text.contains("Ada\t36"), text);
    }

    @Test
    void unsupportedFormatReturnsNull() {
        assertFalse(DocumentText.isExtractable(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "a.docx"));
        assertNull(DocumentText.extract(
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "a.docx", "zip".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void corruptPayloadReturnsNullNeverThrows() {
        assertTrue(DocumentText.isExtractable(XLSX_MIME, "book.xlsx"));
        assertNull(DocumentText.extract(XLSX_MIME, "book.xlsx",
                "not a zip at all".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void oversizePayloadReturnsNull() {
        byte[] huge = new byte[(int) DocumentText.MAX_EXTRACT_BYTES + 1];
        assertNull(DocumentText.extract(XLSX_MIME, "book.xlsx", huge));
    }

    @Test
    void imageOnlyPdfYieldsNullSoCallerDegrades() {
        // Valid container, no text-showing operators → no text to inline.
        String pdf = "%PDF-1.4\n1 0 obj<</Type/Catalog>>endobj\n"
                + "2 0 obj<</Length 8>>stream\nq 1 0 0 1 0 0 cm\nQ\nendstream\nendobj\n%%EOF";
        assertNull(DocumentText.extract("application/pdf", "scan.pdf",
                pdf.getBytes(StandardCharsets.ISO_8859_1)));
    }

    /** Smallest valid xlsx: sharedStrings + one sheet, name/age header + one row. */
    public static byte[] minimalXlsx() throws Exception {
        String shared = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<si><t>name</t></si><si><t>age</t></si><si><t>Ada</t></si></sst>";
        String sheet = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
                + "<sheetData>"
                + "<row r=\"1\"><c r=\"A1\" t=\"s\"><v>0</v></c><c r=\"B1\" t=\"s\"><v>1</v></c></row>"
                + "<row r=\"2\"><c r=\"A2\" t=\"s\"><v>2</v></c><c r=\"B2\"><v>36</v></c></row>"
                + "</sheetData></worksheet>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            zos.putNextEntry(new ZipEntry("xl/sharedStrings.xml"));
            zos.write(shared.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("xl/worksheets/sheet1.xml"));
            zos.write(sheet.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
        return bos.toByteArray();
    }

    /** Minimal PDF with one uncompressed content stream showing "Hello PDF text". */
    public static byte[] minimalPdf() {
        String pdf = "%PDF-1.4\n"
                + "1 0 obj<</Type/Catalog>>endobj\n"
                + "2 0 obj<</Length 30>>stream\n"
                + "BT (Hello PDF text) Tj ET\n"
                + "endstream\nendobj\n%%EOF";
        return pdf.getBytes(StandardCharsets.ISO_8859_1);
    }
}
