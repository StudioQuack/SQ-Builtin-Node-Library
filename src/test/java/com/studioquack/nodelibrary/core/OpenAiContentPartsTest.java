package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2 media blocks → OpenAI content parts under the FULL (real OpenAI) and BASIC
 * (OpenAI-compatible local/third-party: text + image_url only) profiles.
 */
class OpenAiContentPartsTest {

    private static String b64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<String, Object> v2Block(String kind, String mime, String data, String name) {
        return Map.of("type", kind, "mime_type", mime, "source", "base64", "data", data, "name", name);
    }

    // ---- images -------------------------------------------------------------

    @Test
    void imageBlockBecomesImageUrlDataUrl() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("image", "image/png", "aGVsbG8=", "p.png")), null,
                OpenAiContentParts.Profile.BASIC);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) out;
        assertEquals(1, parts.size());
        assertEquals("image_url", parts.get(0).get("type"));
        assertEquals(Map.of("url", "data:image/png;base64,aGVsbG8=", "detail", "auto"),
                parts.get(0).get("image_url"));
    }

    @Test
    void imageBlockWithUrlSourceStaysUrl() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(Map.of("type", "image", "source", "url",
                        "data", "https://example.com/p.png", "name", "p.png")), null,
                OpenAiContentParts.Profile.BASIC);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) out;
        assertEquals(Map.of("url", "https://example.com/p.png", "detail", "auto"),
                parts.get(0).get("image_url"));
    }

    /**
     * Unhydrated handles are skipped (WARN on System.err), not turned into a
     * fake "was not sent" prompt sentence. Hydration is the daemon's job at
     * provider sandbox bake; a leftover handle here means bake did not fill data.
     */
    @Test
    void imageBlockWithoutPayloadBecomesDropNotice() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "sq-file", "fileId", "sq-file:missing", "name", "p.png")), null,
                OpenAiContentParts.Profile.BASIC);
        String text = out instanceof String s ? s : String.valueOf(out);
        assertEquals("", text);
        assertFalse(text.contains("was not sent to the model"));
    }

    @Test
    void unhydratedHandleOnFilesListIsSkipped() {
        Object out = OpenAiContentParts.contentToOpenAi(
                "hi",
                List.of(Map.of("kind", "image", "mime_type", "image/png",
                        "source", "sq-file", "fileId", "sq-file:missing", "name", "p.png")),
                OpenAiContentParts.Profile.FULL);
        assertEquals("hi", out);
    }

    @Test
    void imageBlockWithoutPayloadOrHandleStillDropNotice() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "base64", "name", "p.png")), null,
                OpenAiContentParts.Profile.BASIC);
        String text = out instanceof String s ? s : String.valueOf(out);
        assertTrue(text.contains("was not sent to the model"), text);
        assertTrue(text.contains("p.png"), text);
    }

    // ---- documents ----------------------------------------------------------

    @Test
    void textLikeDocumentIsInlinedAsTextOnBothProfiles() {
        for (OpenAiContentParts.Profile profile : OpenAiContentParts.Profile.values()) {
            Object out = OpenAiContentParts.contentToOpenAi(
                    List.of(v2Block("document", "text/csv", b64("a,b\n1,2"), "t.csv")), null, profile);
            // Text-only result collapses to a plain string.
            assertTrue(out instanceof String, "expected string content, got " + out.getClass());
            String text = (String) out;
            assertTrue(text.contains("a,b\n1,2"), text);
            assertTrue(text.contains("t.csv"), text);
        }
    }

    @Test
    void pdfDocumentBecomesFilePartOnFullProfile() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "application/pdf", "JVBERi0x", "spec.pdf")), null,
                OpenAiContentParts.Profile.FULL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) out;
        assertEquals("file", parts.get(0).get("type"));
        assertEquals(Map.of("file_data", "data:application/pdf;base64,JVBERi0x", "filename", "spec.pdf"),
                parts.get(0).get("file"));
    }

    @Test
    void pdfDocumentDegradesToNoticeOnBasicProfile() {
        // A PDF with no extractable text (here: not even a real container) still degrades.
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "application/pdf", "JVBERi0x", "spec.pdf")), null,
                OpenAiContentParts.Profile.BASIC);
        assertTrue(out instanceof String);
        String text = (String) out;
        assertTrue(text.contains("spec.pdf"), text);
        assertTrue(text.contains("not sent"), text);
        assertTrue(text.contains("Docling"), text);
    }

    @Test
    void xlsxDocumentIsExtractedToTextOnBasicProfile() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        b64, "costs.xlsx")), null, OpenAiContentParts.Profile.BASIC);
        assertTrue(out instanceof String, "expected inlined text, got " + out.getClass());
        String text = (String) out;
        assertTrue(text.contains("costs.xlsx"), text);
        assertTrue(text.contains("extracted text"), text);
        assertTrue(text.contains("Ada"), text);
        assertFalse(text.contains("not sent"), text);
    }

    @Test
    void xlsxDocumentIsExtractedToTextOnFullProfile() throws Exception {
        // Real OpenAI refuses non-PDF file parts — extracted text beats the refusal notice.
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        b64, "costs.xlsx")), null, OpenAiContentParts.Profile.FULL, true);
        assertTrue(out instanceof String, "expected inlined text, got " + out.getClass());
        String text = (String) out;
        assertTrue(text.contains("Ada"), text);
        assertTrue(text.contains("extracted text"), text);
        assertFalse(text.contains("not sent"), text);
        assertFalse(text.contains("\"type\":\"file\""), text);
    }

    @Test
    void xlsxOnVisionModelNeverBecomesNativeFilePart() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        b64, "costs.xlsx")), null, OpenAiContentParts.Profile.FULL, true);
        assertTrue(out instanceof String);
        assertFalse(hasFilePart(out));
    }

    @Test
    void pdfOnVisionModelMayStayNativeFilePart() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "application/pdf", "JVBERi0x", "spec.pdf")), null,
                OpenAiContentParts.Profile.FULL, true);
        assertTrue(hasFilePart(out));
    }

    @Test
    void pdfOnNonVisionModelExtractsOrNotices() {
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalPdf());
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "application/pdf", b64, "spec.pdf")), null,
                OpenAiContentParts.Profile.FULL, false);
        assertFalse(hasFilePart(out));
        String text = out instanceof String s ? s : String.valueOf(out);
        assertTrue(text.contains("spec.pdf") || text.contains("Hello PDF text") || text.contains("not sent"),
                text);
        assertTrue(text.contains("extracted text") || text.contains("not sent"), text);
    }

    @Test
    void docxIsNoticeNeverNativeFilePart() {
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        b64("pk-zip-bytes"), "spec.docx")), null,
                OpenAiContentParts.Profile.FULL, true);
        assertFalse(hasFilePart(out));
        String text = out instanceof String s ? s : String.valueOf(out);
        assertTrue(text.contains("spec.docx"), text);
        assertTrue(text.contains("not sent"), text);
    }

    private static boolean hasFilePart(Object out) {
        if (!(out instanceof List<?> list)) {
            return false;
        }
        for (Object p : list) {
            if (p instanceof Map<?, ?> m && "file".equals(m.get("type"))) {
                return true;
            }
        }
        return false;
    }

    @Test
    void pdfWithTextIsExtractedOnBasicProfile() {
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalPdf());
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "application/pdf", b64, "spec.pdf")), null,
                OpenAiContentParts.Profile.BASIC);
        assertTrue(out instanceof String);
        String text = (String) out;
        assertTrue(text.contains("Hello PDF text"), text);
        assertFalse(text.contains("not sent"), text);
    }

    @Test
    void docxDocumentStillDegradesToNoticeOnBasicProfile() {
        // No in-JVM docx extractor — the Docling notice stays the boundary.
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        b64("pk-zip-bytes"), "spec.docx")), null, OpenAiContentParts.Profile.BASIC);
        assertTrue(out instanceof String);
        String text = (String) out;
        assertTrue(text.contains("spec.docx"), text);
        assertTrue(text.contains("not sent"), text);
        assertTrue(text.contains("Docling"), text);
    }

    @Test
    void audioBecomesInputAudioOnFullAndNoticeOnBasic() {
        Map<String, Object> audio = v2Block("audio", "audio/wav", "UklGRg==", "v.wav");
        Object full = OpenAiContentParts.contentToOpenAi(List.of(audio), null,
                OpenAiContentParts.Profile.FULL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fullParts = (List<Map<String, Object>>) full;
        assertEquals("input_audio", fullParts.get(0).get("type"));
        assertEquals(Map.of("data", "UklGRg==", "format", "wav"), fullParts.get(0).get("input_audio"));

        Object basic = OpenAiContentParts.contentToOpenAi(List.of(audio), null,
                OpenAiContentParts.Profile.BASIC);
        assertTrue(basic instanceof String);
        assertTrue(((String) basic).contains("v.wav"));
    }

    // ---- message walk -------------------------------------------------------

    @Test
    void textOnlyMessagesPassThroughVerbatim() {
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "hi"));
        List<Map<String, Object>> out = OpenAiContentParts.messagesToOpenAi(messages,
                OpenAiContentParts.Profile.BASIC);
        assertEquals("hi", out.get(0).get("content"));
    }

    @Test
    void messageFilesArrayFoldsIntoContentAndFilesKeyIsRemoved() {
        Map<String, Object> file = Map.of(
                "kind", "image", "mime_type", "image/jpeg", "source", "base64",
                "data", "/9j/4AAQ", "name", "shot.jpg");
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "what is this?", "files", List.of(file)));
        List<Map<String, Object>> out = OpenAiContentParts.messagesToOpenAi(messages,
                OpenAiContentParts.Profile.BASIC);
        assertFalse(out.get(0).containsKey("files"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) out.get(0).get("content");
        assertEquals(2, parts.size());
        assertEquals("text", parts.get(0).get("type"));
        assertEquals("image_url", parts.get(1).get("type"));
        assertEquals("data:image/jpeg;base64,/9j/4AAQ",
                ProviderStream.asMap(parts.get(1).get("image_url")).get("url"));
    }

    @Test
    void messagesToOpenAiExtractsOfficeInAnyUserTurn() throws Exception {
        String b64 = Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", List.of(v2Block("document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                b64, "costs.xlsx"))));
        messages.add(Map.of("role", "assistant", "content", "ok"));
        List<Map<String, Object>> out = OpenAiContentParts.messagesToOpenAi(
                messages, OpenAiContentParts.Profile.FULL, true);
        assertFalse(hasFilePart(out.get(0).get("content")));
        String text = String.valueOf(out.get(0).get("content"));
        assertTrue(text.contains("extracted text"), text);
        assertTrue(text.contains("Ada"), text);
    }

    @Test
    void nativeOpenAiPartsPassThroughUntouched() {
        Map<String, Object> nativeFile = Map.of("type", "file",
                "file", Map.of("file_data", "data:application/pdf;base64,JVBERi0x", "filename", "a.pdf"));
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(nativeFile), null, OpenAiContentParts.Profile.BASIC);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) out;
        assertEquals(nativeFile, parts.get(0));
    }

    @Test
    void oversizedTextDocumentIsCapped() {
        String big = "x".repeat(OpenAiContentParts.MAX_INLINE_TEXT_CHARS + 5000);
        Object out = OpenAiContentParts.contentToOpenAi(
                List.of(v2Block("document", "text/plain", b64(big), "big.txt")), null,
                OpenAiContentParts.Profile.BASIC);
        String text = (String) out;
        assertTrue(text.length() < big.length() + 512, "decoded text must be capped");
        assertTrue(text.contains("truncated"));
    }

    // ---- appendFilesToLastUserMessage ---------------------------------------

    @Test
    void appendFilesToLastUserMessageTwoArgAttachesFilesKey() {
        // 2-arg is the attach-only helper Local LLM already calls; do not convert.
        List<Object> out = OpenAiContentParts.appendFilesToLastUserMessage(
                List.of(Map.of("role", "user", "content", "see this")),
                List.of(Map.of("kind", "image", "mime_type", "image/png",
                        "source", "base64", "data", "iVBORw0KGgo=", "name", "x.png")));
        Map<String, Object> msg = ProviderStream.asMap(out.get(0));
        assertEquals("see this", msg.get("content"));
        assertEquals(1, ProviderStream.asList(msg.get("files")).size());
    }

    @Test
    void appendFilesToLastUserMessageFoldsImageAsImageUrlOnFull() {
        List<Object> messages = new java.util.ArrayList<>();
        messages.add(Map.of("role", "user", "content", "see this"));
        List<Object> files = List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64",
                "data", "iVBORw0KGgo=", "name", "x.png"));
        List<Object> out = OpenAiContentParts.appendFilesToLastUserMessage(
                messages, files, OpenAiContentParts.Profile.FULL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) ProviderStream.asMap(out.get(0)).get("content");
        assertEquals("see this", parts.get(0).get("text"));
        assertEquals("image_url", parts.get(1).get("type"));
        assertEquals("data:image/png;base64,iVBORw0KGgo=",
                ProviderStream.asMap(parts.get(1).get("image_url")).get("url"));
        assertFalse(ProviderStream.asMap(out.get(0)).containsKey("files"));
    }

    @Test
    void appendFilesToLastUserMessageNoopsOnNullOrEmpty() {
        List<Object> messages = List.of(Map.of("role", "user", "content", "hi"));
        assertTrue(messages == OpenAiContentParts.appendFilesToLastUserMessage(
                messages, null, OpenAiContentParts.Profile.FULL));
        assertTrue(messages == OpenAiContentParts.appendFilesToLastUserMessage(
                messages, List.of(), OpenAiContentParts.Profile.FULL));
    }

    @Test
    void appendFilesToLastUserMessageCreatesUserMessageWhenNone() {
        List<Object> out = OpenAiContentParts.appendFilesToLastUserMessage(List.of(), List.of(Map.of(
                "kind", "image", "mime_type", "image/png", "source", "base64",
                "data", "iVBORw0KGgo=", "name", "x.png")), OpenAiContentParts.Profile.FULL);
        assertEquals("user", ProviderStream.asMap(out.get(0)).get("role"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) ProviderStream.asMap(out.get(0)).get("content");
        assertEquals("image_url", parts.get(0).get("type"));
    }

    @Test
    void appendFilesToLastUserMessageFoldsAudioAsInputAudioOnFull() {
        List<Object> out = OpenAiContentParts.appendFilesToLastUserMessage(
                List.of(Map.of("role", "user", "content", "transcribe")),
                List.of(Map.of("kind", "audio", "mime_type", "audio/wav", "source", "base64",
                        "data", "UklGRg==", "name", "v.wav")),
                OpenAiContentParts.Profile.FULL);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) ProviderStream.asMap(out.get(0)).get("content");
        assertEquals("input_audio", parts.get(1).get("type"));
        assertEquals(Map.of("data", "UklGRg==", "format", "wav"), parts.get(1).get("input_audio"));
    }
}
