package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * v2 → native request mapping per {@code stream.format}. Each format is a pure
 * {@code Map}→{@code Map} transform; {@code map(map(x))} equals {@code map(x)}.
 */
class ProviderRequestMapperTest {

    @Test
    void openaiSseIsIdentity() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.2);
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        assertEquals(body, out);
        assertEquals(out, ProviderRequestMapper.map("openai-sse", out));
    }

    @Test
    void unknownFormatIsIdentity() {
        Map<String, Object> body = Map.of("messages", List.of());
        assertEquals(body, ProviderRequestMapper.map("nope", body));
        assertEquals(body, ProviderRequestMapper.map(null, body));
    }

    // ---- gemini-sse --------------------------------------------------------

    @Test
    void geminiMapsMessagesSystemSamplingAndTools() {
        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", "be terse"),
                        Map.of("role", "user", "content", "hi"),
                        Map.of("role", "assistant", "content", "hello")),
                "temperature", 0.4,
                "top_p", 0.9,
                "max_tokens", 128,
                "stop", List.of("END"),
                "tools", List.of(Map.of(
                        "name", "lookup",
                        "description", "find it",
                        "request", Map.of("type", "object",
                                "properties", Map.of("q", Map.of("type", "string"))))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);

        assertEquals(List.of(
                Map.of("role", "user", "parts", List.of(Map.of("text", "hi"))),
                Map.of("role", "model", "parts", List.of(Map.of("text", "hello")))),
                out.get("contents"));
        assertEquals(Map.of("parts", List.of(Map.of("text", "be terse"))), out.get("systemInstruction"));
        Map<?, ?> cfg = (Map<?, ?>) out.get("generationConfig");
        assertEquals(0.4, cfg.get("temperature"));
        assertEquals(0.9, cfg.get("topP"));
        assertEquals(128, cfg.get("maxOutputTokens"));
        assertEquals(List.of("END"), cfg.get("stopSequences"));
        assertFalse(out.containsKey("messages"));
        assertFalse(out.containsKey("temperature"));

        List<?> tools = (List<?>) out.get("tools");
        Map<?, ?> wrapper = (Map<?, ?>) tools.get(0);
        List<?> decls = (List<?>) wrapper.get("functionDeclarations");
        Map<?, ?> decl = (Map<?, ?>) decls.get(0);
        assertEquals("lookup", decl.get("name"));
        assertEquals("find it", decl.get("description"));
        assertEquals(Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))),
                decl.get("parameters"));

        assertEquals(out, ProviderRequestMapper.map("gemini-sse", out));
    }

    @Test
    void geminiContentsAlreadyPresentIsUntouched() {
        Map<String, Object> nativeBody = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", "x")))),
                "messages", List.of(Map.of("role", "user", "content", "should-not-rewrite")),
                "temperature", 0.1);
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", nativeBody);
        assertEquals(nativeBody.get("contents"), out.get("contents"));
        assertEquals(nativeBody.get("messages"), out.get("messages"));
        assertEquals(0.1, out.get("temperature"));
        assertFalse(out.containsKey("generationConfig"));
    }

    @Test
    void geminiMultimodalBase64AndUrl() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "look"),
                        Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                                "data", "aGVsbG8="),
                        Map.of("type", "image", "mime_type", "image/jpeg", "source", "url",
                                "data", "https://example.test/a.jpg")))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);
        List<?> contents = (List<?>) out.get("contents");
        Map<?, ?> row = (Map<?, ?>) contents.get(0);
        List<?> parts = (List<?>) row.get("parts");
        assertEquals(Map.of("text", "look"), parts.get(0));
        assertEquals(Map.of("inline_data", Map.of("mime_type", "image/png", "data", "aGVsbG8=")), parts.get(1));
        assertEquals(Map.of("file_data", Map.of("mime_type", "image/jpeg",
                "file_uri", "https://example.test/a.jpg")), parts.get(2));
        assertEquals(out, ProviderRequestMapper.map("gemini-sse", out));
    }

    /** An image whose bytes never arrived must surface as a text notice, not vanish. */
    @Test
    void geminiUnhydratedImageBecomesTextNotice() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "sq-file", "fileId", "sq-file:missing", "name", "p.png")))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);
        List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) out.get("contents")).get(0)).get("parts");
        assertEquals(1, parts.size());
        Map<?, ?> part = (Map<?, ?>) parts.get(0);
        assertFalse(part.containsKey("inline_data"), "no empty blob may reach the API: " + part);
        String text = String.valueOf(part.get("text"));
        assertTrue(text.contains("was not sent to the model"), text);
        assertTrue(text.contains("p.png"), text);
    }

    /** Same unhydrated-image gap on the Anthropic path: notice, never a silent drop. */
    @Test
    void anthropicUnhydratedImageBecomesNotice() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "image", "mime_type", "image/png",
                        "source", "sq-file", "fileId", "sq-file:missing", "name", "p.png")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        List<?> messages = (List<?>) out.get("messages");
        Object content = ((Map<?, ?>) messages.get(0)).get("content");
        // All-text content collapses to a plain string; either shape must carry the notice.
        String text = content instanceof String s ? s : String.valueOf(content);
        assertTrue(text.contains("was not sent to the model"), text);
        assertTrue(text.contains("p.png"), text);
    }

    /** Ollama folds media into text+images: an unhydrated image lands in the text, not nowhere. */
    @Test
    void ollamaUnhydratedImageFoldsNoticeIntoText() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(
                        Map.of("type", "text", "text", "what is in this picture?"),
                        Map.of("type", "image", "mime_type", "image/png",
                                "source", "sq-file", "fileId", "sq-file:missing", "name", "p.png")))));
        Map<String, Object> out = ProviderRequestMapper.map("ollama-ndjson", body);
        Map<?, ?> message = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        String content = String.valueOf(message.get("content"));
        assertTrue(content.contains("was not sent to the model"), content);
        assertTrue(content.contains("what is in this picture?"), content);
        assertTrue(message.get("images") == null
                        || ((List<?>) message.get("images")).isEmpty(),
                "no empty image entry may reach the API: " + message.get("images"));
    }

    @Test
    void geminiDataUrlStripsHeader() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "image",
                        "data", "data:image/gif;base64,R0lG")))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);
        List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) out.get("contents")).get(0)).get("parts");
        Map<?, ?> blob = (Map<?, ?>) ((Map<?, ?>) parts.get(0)).get("inline_data");
        assertEquals("image/gif", blob.get("mime_type"));
        assertEquals("R0lG", blob.get("data"));
    }

    @Test
    void geminiXlsxDocumentIsExtractedToTextPart() throws Exception {
        // Gemini inline_data refuses office binaries — extracted text must travel instead.
        String b64 = java.util.Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user", "content", "summarize",
                "files", List.of(Map.of(
                        "kind", "document",
                        "mime_type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        "source", "base64", "data", b64, "name", "costs.xlsx")))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);
        List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) out.get("contents")).get(0)).get("parts");
        Map<?, ?> last = (Map<?, ?>) parts.get(parts.size() - 1);
        String text = (String) last.get("text");
        assertTrue(text.contains("costs.xlsx"), text);
        assertTrue(text.contains("Ada"), text);
        assertFalse(last.containsKey("inline_data"));
        assertEquals(out, ProviderRequestMapper.map("gemini-sse", out));
    }

    @Test
    void geminiPdfDocumentStaysInlineData() {
        Map<String, Object> body = Map.of("messages", List.of(Map.of(
                "role", "user",
                "content", List.of(Map.of("type", "document", "mime_type", "application/pdf",
                        "source", "base64", "data", "JVBERi0x", "name", "s.pdf")))));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body);
        List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) out.get("contents")).get(0)).get("parts");
        assertEquals(Map.of("inline_data", Map.of("mime_type", "application/pdf", "data", "JVBERi0x")),
                parts.get(0));
    }

    // ---- anthropic-sse -----------------------------------------------------

    @Test
    void anthropicExtractsSystemRequiresMaxTokensAndMapsTools() {
        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", "stay calm"),
                        Map.of("role", "user", "content", "hi")),
                "temperature", 0.3,
                "top_p", 0.8,
                "top_k", 20,
                "thinking", Map.of("type", "enabled", "budget_tokens", 1024),
                "tools", List.of(Map.of(
                        "name", "search",
                        "description", "q",
                        "request", Map.of("type", "object", "properties", Map.of()))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        assertEquals("stay calm", out.get("system"));
        List<?> msgs = (List<?>) out.get("messages");
        assertEquals(1, msgs.size());
        assertEquals("user", ((Map<?, ?>) msgs.get(0)).get("role"));
        assertEquals("hi", ((Map<?, ?>) msgs.get(0)).get("content"));
        assertEquals(ProviderRequestMapper.ANTHROPIC_DEFAULT_MAX_TOKENS, out.get("max_tokens"));
        assertEquals(0.3, out.get("temperature"));
        assertEquals(0.8, out.get("top_p"));
        assertEquals(20, out.get("top_k"));
        assertEquals(Map.of("type", "enabled", "budget_tokens", 1024), out.get("thinking"));
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) out.get("tools")).get(0);
        assertEquals("search", tool.get("name"));
        assertEquals("q", tool.get("description"));
        assertEquals(Map.of("type", "object", "properties", Map.of()), tool.get("input_schema"));
        assertFalse(tool.containsKey("request"));
        assertEquals(out, ProviderRequestMapper.map("anthropic-sse", out));
    }

    @Test
    void anthropicPreservesExistingMaxTokensAndNativeTools() {
        Map<String, Object> nativeBody = Map.of(
                "system", "already",
                "max_tokens", 256,
                "messages", List.of(Map.of("role", "user", "content",
                        List.of(Map.of("type", "text", "text", "hi")))),
                "tools", List.of(Map.of("name", "t", "description", "d",
                        "input_schema", Map.of("type", "object"))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", nativeBody);
        assertEquals(256, out.get("max_tokens"));
        assertEquals("already", out.get("system"));
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) out.get("tools")).get(0);
        assertEquals(Map.of("type", "object"), tool.get("input_schema"));
        assertEquals(out, ProviderRequestMapper.map("anthropic-sse", out));
    }

    @Test
    void anthropicStopAliasAndMultimodalBlocks() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "see"),
                                Map.of("type", "image", "mime_type", "image/png",
                                        "source", "base64", "data", "QQ=="),
                                Map.of("type", "image", "source", "url",
                                        "data", "https://example.test/x.png")))),
                "stop", "END",
                "max_tokens", 10);
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        assertEquals(List.of("END"), out.get("stop_sequences"));
        assertFalse(out.containsKey("stop"));
        List<?> blocks = (List<?>) ((Map<?, ?>) ((List<?>) out.get("messages")).get(0)).get("content");
        assertEquals(Map.of("type", "text", "text", "see"), blocks.get(0));
        Map<?, ?> img = (Map<?, ?>) blocks.get(1);
        assertEquals("image", img.get("type"));
        assertEquals(Map.of("type", "base64", "media_type", "image/png", "data", "QQ=="), img.get("source"));
        Map<?, ?> url = (Map<?, ?>) blocks.get(2);
        assertEquals(Map.of("type", "url", "url", "https://example.test/x.png"), url.get("source"));
    }

    // ---- ollama-ndjson -----------------------------------------------------

    @Test
    void ollamaNestsSamplingIntoOptionsAndKeepsThink() {
        Map<String, Object> body = Map.of(
                "model", "llama",
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.5,
                "top_p", 0.9,
                "seed", 7,
                "stop", List.of("\n"),
                "max_tokens", 64,
                "think", true);
        Map<String, Object> out = ProviderRequestMapper.map("ollama-ndjson", body);
        assertEquals(List.of(Map.of("role", "user", "content", "hi")), out.get("messages"));
        assertEquals(Boolean.TRUE, out.get("think"));
        Map<?, ?> options = (Map<?, ?>) out.get("options");
        assertEquals(0.5, options.get("temperature"));
        assertEquals(0.9, options.get("top_p"));
        assertEquals(7, options.get("seed"));
        assertEquals(List.of("\n"), options.get("stop"));
        assertEquals(64, options.get("num_predict"));
        assertFalse(out.containsKey("temperature"));
        assertFalse(out.containsKey("max_tokens"));
        assertEquals(out, ProviderRequestMapper.map("ollama-ndjson", out));
    }

    @Test
    void ollamaDoesNotClobberExistingOptions() {
        Map<String, Object> body = Map.of(
                "options", Map.of("temperature", 0.1, "num_predict", 8),
                "temperature", 0.9,
                "max_tokens", 99);
        Map<String, Object> out = ProviderRequestMapper.map("ollama-ndjson", body);
        Map<?, ?> options = (Map<?, ?>) out.get("options");
        assertEquals(0.1, options.get("temperature"));
        assertEquals(8, options.get("num_predict"));
    }

    // ---- cohere-sse --------------------------------------------------------

    @Test
    void cohereMapsOpenaiStopAndSamplingAliases() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "stop", List.of("END"),
                "top_p", 0.75,
                "top_k", 5,
                "temperature", 0.3);
        Map<String, Object> out = ProviderRequestMapper.map("cohere-sse", body);
        assertEquals(List.of(Map.of("role", "user", "content", "hi")), out.get("messages"));
        assertEquals(List.of("END"), out.get("stop_sequences"));
        assertEquals(0.75, out.get("p"));
        assertEquals(5, out.get("k"));
        assertEquals(0.3, out.get("temperature"));
        assertFalse(out.containsKey("stop"));
        assertFalse(out.containsKey("top_p"));
        assertEquals(out, ProviderRequestMapper.map("cohere-sse", out));
    }

    @Test
    void cohereDoesNotOverwriteNativeStopSequences() {
        Map<String, Object> body = Map.of(
                "stop_sequences", List.of("NATIVE"),
                "stop", List.of("OPENAI"),
                "p", 0.5,
                "top_p", 0.9);
        Map<String, Object> out = ProviderRequestMapper.map("cohere-sse", body);
        assertEquals(List.of("NATIVE"), out.get("stop_sequences"));
        assertEquals(0.5, out.get("p"));
        assertFalse(out.containsKey("stop"));
        assertFalse(out.containsKey("top_p"));
    }

    @Test
    void nullBodyYieldsEmptyMap() {
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", null);
        assertTrue(out.isEmpty());
        assertNull(out.get("messages"));
    }

    @Test
    void cohereMapsV2ToolsToFunctionWrapper() {
        Map<String, Object> body = Map.of("tools", List.of(Map.of(
                "name", "lookup",
                "description", "find",
                "request", Map.of("type", "object"))));
        Map<String, Object> out = ProviderRequestMapper.map("cohere-sse", body);
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) out.get("tools")).get(0);
        assertEquals("function", tool.get("type"));
        Map<?, ?> fn = (Map<?, ?>) tool.get("function");
        assertEquals("lookup", fn.get("name"));
        assertEquals(Map.of("type", "object"), fn.get("parameters"));
        assertEquals(out, ProviderRequestMapper.map("cohere-sse", out));
    }

    // ---- responses-sse -----------------------------------------------------

    @Test
    void responsesMapsMessagesToInputAndSystemToInstructions() {
        Map<String, Object> body = Map.of(
                "messages", List.of(
                        Map.of("role", "system", "content", "be terse"),
                        Map.of("role", "user", "content", "hi"),
                        Map.of("role", "assistant", "content", "hello")),
                "temperature", 0.4,
                "top_p", 0.9,
                "max_tokens", 128);
        Map<String, Object> out = ProviderRequestMapper.map("responses-sse", body);
        assertEquals("be terse", out.get("instructions"));
        assertEquals(List.of(
                Map.of("type", "message", "role", "user",
                        "content", List.of(Map.of("type", "input_text", "text", "hi"))),
                Map.of("type", "message", "role", "assistant",
                        "content", List.of(Map.of("type", "input_text", "text", "hello")))),
                out.get("input"));
        assertEquals(0.4, out.get("temperature"));
        assertEquals(0.9, out.get("top_p"));
        assertEquals(128, out.get("max_output_tokens"));
        assertFalse(out.containsKey("messages"));
        assertFalse(out.containsKey("max_tokens"));
        assertEquals(out, ProviderRequestMapper.map("responses-sse", out));
    }

    @Test
    void responsesInputAlreadyPresentIsUntouched() {
        Map<String, Object> nativeBody = Map.of(
                "input", List.of(Map.of("role", "user", "content", "x")),
                "messages", List.of(Map.of("role", "user", "content", "should-not-rewrite")),
                "temperature", 0.1);
        Map<String, Object> out = ProviderRequestMapper.map("responses-sse", nativeBody);
        assertEquals(nativeBody.get("input"), out.get("input"));
        assertEquals(nativeBody.get("messages"), out.get("messages"));
        assertEquals(0.1, out.get("temperature"));
        assertEquals(out, ProviderRequestMapper.map("responses-sse", out));
    }

    @Test
    void responsesMapsV2ToolsToFunctionShape() {
        Map<String, Object> body = Map.of("tools", List.of(Map.of(
                "name", "lookup",
                "description", "find it",
                "request", Map.of("type", "object",
                        "properties", Map.of("q", Map.of("type", "string"))))));
        Map<String, Object> out = ProviderRequestMapper.map("responses-sse", body);
        Map<?, ?> tool = (Map<?, ?>) ((List<?>) out.get("tools")).get(0);
        assertEquals("function", tool.get("type"));
        assertEquals("lookup", tool.get("name"));
        assertEquals("find it", tool.get("description"));
        assertEquals(Map.of("type", "object", "properties", Map.of("q", Map.of("type", "string"))),
                tool.get("parameters"));
        assertFalse(tool.containsKey("function"));
        assertFalse(tool.containsKey("request"));
        assertEquals(out, ProviderRequestMapper.map("responses-sse", out));
    }

    @Test
    void responsesDoesNotOverwriteExistingMaxOutputTokens() {
        Map<String, Object> body = Map.of(
                "max_output_tokens", 64,
                "max_tokens", 99);
        Map<String, Object> out = ProviderRequestMapper.map("responses-sse", body);
        assertEquals(64, out.get("max_output_tokens"));
        assertFalse(out.containsKey("max_tokens"));
    }

    // ---- reasoning auto-enable --------------------------------------------

    @Test
    void anthropicAutoEnablesThinkingForCapableModels() {
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.7,
                "top_p", 0.9,
                "top_k", 40);
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> thinking = (Map<?, ?>) out.get("thinking");
        assertEquals("enabled", thinking.get("type"));
        long budget = ((Number) thinking.get("budget_tokens")).longValue();
        assertTrue(budget >= 1024, "budget floor");
        assertTrue(budget < ProviderRequestMapper.ANTHROPIC_DEFAULT_MAX_TOKENS, "budget < max_tokens");
        // Extended thinking rejects sampling overrides — dropped on auto-enable.
        assertFalse(out.containsKey("temperature"));
        assertFalse(out.containsKey("top_p"));
        assertFalse(out.containsKey("top_k"));
        assertEquals(out, ProviderRequestMapper.map("anthropic-sse", out));
    }

    @Test
    void anthropicThinkingBudgetFollowsMaxTokens() {
        Map<String, Object> body = Map.of(
                "model", "claude-3-7-sonnet-latest",
                "max_tokens", 4096,
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> thinking = (Map<?, ?>) out.get("thinking");
        assertEquals(2048L, ((Number) thinking.get("budget_tokens")).longValue());
    }

    @Test
    void anthropicSkipsThinkingForLegacyModelsAndTinyBudgets() {
        Map<String, Object> legacy = Map.of(
                "model", "claude-3-5-sonnet-latest",
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.7);
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", legacy);
        assertFalse(out.containsKey("thinking"));
        assertEquals(0.7, out.get("temperature"));

        Map<String, Object> tiny = Map.of(
                "model", "claude-sonnet-4-5",
                "max_tokens", 512,
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        Map<String, Object> out2 = ProviderRequestMapper.map("anthropic-sse", tiny);
        assertFalse(out2.containsKey("thinking"), "no room for budget_tokens < max_tokens");
    }

    @Test
    void anthropicDocumentFileItemStaysDocumentNotImage() {
        // files-list items carry `kind` (not `type`): a PDF must become a document block.
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of(
                        "role", "user", "content", "summarize",
                        "files", List.of(Map.of(
                                "kind", "document", "mime_type", "application/pdf",
                                "source", "base64", "data", "JVBERi0x", "name", "s.pdf")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        List<?> content = (List<?>) user.get("content");
        Map<?, ?> doc = (Map<?, ?>) content.get(content.size() - 1);
        assertEquals("document", doc.get("type"));
        Map<?, ?> source = (Map<?, ?>) doc.get("source");
        assertEquals("base64", source.get("type"));
        assertEquals("application/pdf", source.get("media_type"));
        assertEquals("JVBERi0x", source.get("data"));
    }

    @Test
    void anthropicXlsxDocumentIsExtractedToTextSource() throws Exception {
        // The Messages API refuses office binaries — deliver extracted text in the
        // source.type:"text" document form instead of dropping the attachment.
        String b64 = java.util.Base64.getEncoder().encodeToString(
                com.studioquack.nodelibrary.nodes.codec.DocumentTextTest.minimalXlsx());
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of(
                        "role", "user", "content", "summarize",
                        "files", List.of(Map.of(
                                "kind", "document",
                                "mime_type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                                "source", "base64", "data", b64, "name", "costs.xlsx")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        List<?> content = (List<?>) user.get("content");
        Map<?, ?> doc = (Map<?, ?>) content.get(content.size() - 1);
        assertEquals("document", doc.get("type"));
        Map<?, ?> source = (Map<?, ?>) doc.get("source");
        assertEquals("text", source.get("type"));
        assertEquals("text/plain", source.get("media_type"));
        String data = (String) source.get("data");
        assertTrue(data.contains("Ada"), data);
        assertTrue(String.valueOf(doc.get("title")).contains("costs.xlsx"));
    }

    @Test
    void anthropicAudioBlockDegradesToNoticeNotMalformedImage() {
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "audio", "mime_type", "audio/wav",
                                "source", "base64", "data", "UklGRg==", "name", "v.wav")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        // A lone text block collapses to a plain string in anthropicMessage.
        String content = (String) user.get("content");
        assertTrue(content.contains("v.wav"), content);
        assertTrue(content.contains("not sent"), content);
    }

    @Test
    void anthropicTextDocumentUsesPlainTextSourceNotBase64() {
        // Anthropic base64 documents accept application/pdf ONLY; text/plain must travel
        // as source.type:"text" with plain UTF-8 data (verified against the 2026 docs).
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of(
                        "role", "user", "content", "summarize",
                        "files", List.of(Map.of(
                                "kind", "document", "mime_type", "text/markdown",
                                "source", "base64",
                                "data", java.util.Base64.getEncoder().encodeToString(
                                        "# Report".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                "name", "r.md")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        List<?> content = (List<?>) user.get("content");
        Map<?, ?> doc = (Map<?, ?>) content.get(content.size() - 1);
        assertEquals("document", doc.get("type"));
        assertEquals("r.md", doc.get("title"));
        Map<?, ?> source = (Map<?, ?>) doc.get("source");
        assertEquals("text", source.get("type"));
        assertEquals("text/plain", source.get("media_type"));
        assertEquals("# Report", source.get("data"));
    }

    @Test
    void anthropicBinaryDocumentAndUnsupportedImageBecomeNotices() {
        Map<String, Object> body = Map.of(
                "model", "claude-sonnet-4-5",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "document",
                                        "mime_type", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                                        "source", "base64", "data", "UEsDBBQ=", "name", "spec.docx"),
                                Map.of("type", "image", "mime_type", "image/bmp",
                                        "source", "base64", "data", "Qk0=", "name", "b.bmp")))));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        List<?> content = (List<?>) user.get("content");
        for (Object c : content) {
            assertEquals("text", ((Map<?, ?>) c).get("type"),
                    "unsupported payloads must degrade to text notices, not provider 400s");
        }
        assertTrue(String.valueOf(((Map<?, ?>) content.get(0)).get("text")).contains("spec.docx"));
        assertTrue(String.valueOf(((Map<?, ?>) content.get(1)).get("text")).contains("b.bmp"));
    }

    @Test
    void geminiAddsThinkingConfigForThinkingModels() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.4);
        // model arrives via the context map: Gemini puts it in the URL path, not the body.
        Map<String, Object> ctx = Map.of("model", "gemini-2.5-flash");
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body, ctx);
        Map<?, ?> gen = (Map<?, ?>) out.get("generationConfig");
        assertEquals(0.4, gen.get("temperature"));
        assertEquals(Map.of("includeThoughts", true, "thinkingBudget", -1), gen.get("thinkingConfig"));
        assertEquals(out, ProviderRequestMapper.map("gemini-sse", out, ctx));
    }

    @Test
    void geminiSkipsThinkingConfigForPlainModelsAndKeepsUserPolicy() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body,
                Map.of("model", "gemini-2.0-flash"));
        assertFalse(out.containsKey("generationConfig"));

        Map<String, Object> userPolicy = Map.of(
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "generationConfig", Map.of("thinkingConfig",
                        Map.of("includeThoughts", true, "thinkingBudget", 2048)));
        Map<String, Object> out2 = ProviderRequestMapper.map("gemini-sse", userPolicy,
                Map.of("model", "gemini-2.5-pro"));
        Map<?, ?> gen = (Map<?, ?>) out2.get("generationConfig");
        assertEquals(2048, ((Map<?, ?>) gen.get("thinkingConfig")).get("thinkingBudget"));
    }

    @Test
    void groqReasoningFormatParsedForRawDefaultFamilies() {
        Map<String, Object> body = Map.of(
                "model", "qwen/qwen3-32b",
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        assertEquals("parsed", out.get("reasoning_format"));
        assertEquals(out, ProviderRequestMapper.map("openai-sse", out));

        Map<String, Object> r1 = Map.of(
                "model", "deepseek-r1-distill-llama-70b",
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        assertEquals("parsed", ProviderRequestMapper.map("openai-sse", r1).get("reasoning_format"));
    }

    @Test
    void groqSkipsGptOssUnprefixedQwenAndNonReasoningModels() {
        Map<String, Object> oss = Map.of(
                "model", "openai/gpt-oss-120b",
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        assertFalse(ProviderRequestMapper.map("openai-sse", oss).containsKey("reasoning_format"));

        // qwen-cloud (DashScope) uses unprefixed ids and its own enable_thinking knob.
        Map<String, Object> dashscope = Map.of(
                "model", "qwen3-32b",
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        assertFalse(ProviderRequestMapper.map("openai-sse", dashscope).containsKey("reasoning_format"));

        Map<String, Object> llama = Map.of(
                "model", "llama-3.3-70b-versatile",
                "messages", List.of(Map.of("role", "user", "content", "hi")));
        assertFalse(ProviderRequestMapper.map("openai-sse", llama).containsKey("reasoning_format"));

        // An explicit caller choice always wins over the auto-default.
        Map<String, Object> raw = Map.of(
                "model", "qwen/qwen3-32b",
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "reasoning_format", "raw");
        assertEquals("raw", ProviderRequestMapper.map("openai-sse", raw).get("reasoning_format"));
    }

    // ---- openai-sse media folding (BASIC profile) ---------------------------

    @Test
    void openaiSseFoldsMediaBlocksIntoOpenAiParts() {
        Map<String, Object> body = Map.of(
                "model", "llama-3.2-vision",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "what is in this image?"),
                                Map.of("type", "image", "mime_type", "image/png",
                                        "source", "base64", "data", "aGVsbG8=", "name", "p.png")))));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        List<?> messages = (List<?>) out.get("messages");
        Map<?, ?> user = (Map<?, ?>) messages.get(0);
        List<?> parts = (List<?>) user.get("content");
        assertEquals(2, parts.size());
        assertEquals("text", ((Map<?, ?>) parts.get(0)).get("type"));
        Map<?, ?> image = (Map<?, ?>) parts.get(1);
        assertEquals("image_url", image.get("type"));
        assertEquals("data:image/png;base64,aGVsbG8=",
                ((Map<?, ?>) image.get("image_url")).get("url"));
        // Idempotent: a second pass sees only native parts and text.
        assertEquals(out, ProviderRequestMapper.map("openai-sse", out));
    }

    @Test
    void openaiSseFoldsMessageFilesAndDropsTheKey() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", "summarize",
                        "files", List.of(Map.of(
                                "kind", "document", "mime_type", "text/plain",
                                "source", "base64",
                                "data", java.util.Base64.getEncoder()
                                        .encodeToString("hello file".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                "name", "notes.txt")))));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        assertFalse(user.containsKey("files"));
        // Text-like document inlines as text → whole content collapses to a string.
        String content = (String) user.get("content");
        assertTrue(content.contains("summarize"), content);
        assertTrue(content.contains("hello file"), content);
    }

    @Test
    void openaiSsePdfBecomesExplicitNoticeNotAProvider400() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(Map.of(
                                "type", "document", "mime_type", "application/pdf",
                                "source", "base64", "data", "JVBERi0x", "name", "spec.pdf")))));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        String content = (String) user.get("content");
        assertTrue(content.contains("spec.pdf"), content);
        assertTrue(content.contains("not sent"), content);
    }

    // ---- ollama-ndjson media flattening --------------------------------------

    @Test
    void ollamaFlattensBlocksAndCollectsImages() {
        Map<String, Object> body = Map.of(
                "model", "llava",
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "describe"),
                                Map.of("type", "image", "mime_type", "image/png",
                                        "source", "base64", "data", "data:image/png;base64,aGVsbG8=",
                                        "name", "p.png")))));
        Map<String, Object> out = ProviderRequestMapper.map("ollama-ndjson", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        assertEquals("describe", user.get("content"));
        assertEquals(List.of("aGVsbG8="), user.get("images"));
    }

    @Test
    void ollamaTextOnlyMessagesPassThrough() {
        Map<String, Object> body = Map.of(
                "model", "llama3",
                "messages", List.of(Map.of("role", "user", "content", "hi")),
                "temperature", 0.3);
        Map<String, Object> out = ProviderRequestMapper.map("ollama-ndjson", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        assertEquals("hi", user.get("content"));
        assertFalse(user.containsKey("images"));
        Map<?, ?> options = (Map<?, ?>) out.get("options");
        assertEquals(0.3, options.get("temperature"));
    }

    // ---- cohere-sse media flattening -----------------------------------------

    @Test
    void cohereFlattensMediaToText() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "read this: "),
                                Map.of("type", "document", "mime_type", "text/markdown",
                                        "source", "base64",
                                        "data", java.util.Base64.getEncoder()
                                                .encodeToString("# Title".getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                                        "name", "doc.md")))));
        Map<String, Object> out = ProviderRequestMapper.map("cohere-sse", body);
        Map<?, ?> user = (Map<?, ?>) ((List<?>) out.get("messages")).get(0);
        String content = (String) user.get("content");
        assertTrue(content.contains("read this: "), content);
        assertTrue(content.contains("# Title"), content);
    }

    // ---- responses-sse media --------------------------------------------------

    @Test
    void responsesMapsImageAndPdfToInputParts() {
        Map<String, Object> body = Map.of(
                "messages", List.of(Map.of(
                        "role", "user",
                        "content", List.of(
                                Map.of("type", "text", "text", "look"),
                                Map.of("type", "image", "mime_type", "image/png",
                                        "source", "base64", "data", "aGVsbG8=", "name", "p.png"),
                                Map.of("type", "document", "mime_type", "application/pdf",
                                        "source", "base64", "data", "JVBERi0x", "name", "s.pdf")))));
        Map<String, Object> out = ProviderRequestMapper.map("responses-sse", body);
        List<?> input = (List<?>) out.get("input");
        Map<?, ?> message = (Map<?, ?>) input.get(0);
        List<?> content = (List<?>) message.get("content");
        assertEquals(3, content.size());
        assertEquals("input_text", ((Map<?, ?>) content.get(0)).get("type"));
        Map<?, ?> image = (Map<?, ?>) content.get(1);
        assertEquals("input_image", image.get("type"));
        assertEquals("data:image/png;base64,aGVsbG8=", image.get("image_url"));
        Map<?, ?> file = (Map<?, ?>) content.get(2);
        assertEquals("input_file", file.get("type"));
        assertEquals("s.pdf", file.get("filename"));
        assertEquals("data:application/pdf;base64,JVBERi0x", file.get("file_data"));
    }

    // ---- inputFiles folding ------------------------------------------------

    private static Map<String, Object> pngFile() {
        return Map.of("kind", "image", "mime_type", "image/png", "source", "base64",
                "data", "aGVsbG8=", "name", "p.png");
    }

    @Test
    void inputFilesOnContextFoldIntoOpenaiSseLastUserMessage() {
        Map<String, Object> body = Map.of("messages", List.of(
                Map.of("role", "system", "content", "be terse"),
                Map.of("role", "user", "content", "look")));
        Map<String, Object> ctx = Map.of(
                "messages", body.get("messages"),
                "inputFiles", List.of(pngFile()));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body, ctx);
        List<?> messages = (List<?>) out.get("messages");
        Map<?, ?> user = (Map<?, ?>) messages.get(1);
        Object content = user.get("content");
        assertTrue(content instanceof List<?>, String.valueOf(content));
        List<?> parts = (List<?>) content;
        assertEquals("text", ((Map<?, ?>) parts.get(0)).get("type"));
        assertEquals("image_url", ((Map<?, ?>) parts.get(1)).get("type"));
        assertFalse(out.containsKey("inputFiles"));
        assertFalse(user.containsKey("files"));
    }

    @Test
    void inputFilesOnContextFoldIntoAnthropicSseLastUserMessage() {
        Map<String, Object> body = Map.of("messages", List.of(
                Map.of("role", "user", "content", "look")));
        Map<String, Object> ctx = Map.of("inputFiles", List.of(pngFile()));
        Map<String, Object> out = ProviderRequestMapper.map("anthropic-sse", body, ctx);
        Object content = ((Map<?, ?>) ((List<?>) out.get("messages")).get(0)).get("content");
        assertTrue(content instanceof List<?>, String.valueOf(content));
        Map<?, ?> image = (Map<?, ?>) ((List<?>) content).get(1);
        assertEquals("image", image.get("type"));
        assertEquals(Map.of("type", "base64", "media_type", "image/png", "data", "aGVsbG8="),
                image.get("source"));
    }

    @Test
    void inputFilesOnContextFoldIntoGeminiSseLastUserMessage() {
        Map<String, Object> body = Map.of("messages", List.of(
                Map.of("role", "user", "content", "look")));
        Map<String, Object> ctx = Map.of("inputFiles", List.of(pngFile()));
        Map<String, Object> out = ProviderRequestMapper.map("gemini-sse", body, ctx);
        List<?> parts = (List<?>) ((Map<?, ?>) ((List<?>) out.get("contents")).get(0)).get("parts");
        assertEquals(Map.of("text", "look"), parts.get(0));
        assertEquals(Map.of("inline_data", Map.of("mime_type", "image/png", "data", "aGVsbG8=")),
                parts.get(1));
    }

    @Test
    void inputFilesOnNoMessagesBodyLeftUnchanged() {
        Map<String, Object> body = Map.of("prompt", "hi", "inputFiles", List.of(pngFile()));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body);
        assertEquals(body, out);
        Map<String, Object> identity = ProviderRequestMapper.map(null, body);
        assertEquals(body, identity);
    }

    @Test
    void inputFilesOnSystemOnlyMessagesAppendsUserMessage() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("messages", List.of(Map.of("role", "system", "content", "rules")));
        Map<String, Object> out = ProviderRequestMapper.map("openai-sse", body,
                Map.of("inputFiles", List.of(pngFile())));
        List<?> messages = (List<?>) out.get("messages");
        assertEquals(2, messages.size());
        Map<?, ?> user = (Map<?, ?>) messages.get(1);
        assertEquals("user", user.get("role"));
        Object content = user.get("content");
        assertTrue(content instanceof List<?>, String.valueOf(content));
        assertEquals("image_url", ((Map<?, ?>) ((List<?>) content).get(0)).get("type"));
    }
}
