package com.studioquack.nodelibrary.nodes.aiagent;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** AgentJson: coercion helpers, tolerant JSON extraction, envelope unwraps, file-block coercion. */
class AgentJsonTest {

    @Test
    void asMapCastsOrReturnsMutableEmpty() {
        Map<String, Object> m = Map.of("a", 1);
        assertSame(m, AgentJson.asMap(m));
        Map<String, Object> empty = AgentJson.asMap("nope");
        assertTrue(empty.isEmpty());
        empty.put("k", "v");
        assertEquals("v", empty.get("k"));
    }

    @Test
    void mutableMapCopies() {
        Map<String, Object> copy = AgentJson.mutableMap(Map.of("a", 1));
        copy.put("b", 2);
        assertEquals(Map.of("a", 1, "b", 2), copy);
    }

    @Test
    void asListCastsOrReturnsEmpty() {
        List<Object> l = List.of(1, 2);
        assertSame(l, AgentJson.asList(l));
        assertTrue(AgentJson.asList(Map.of()).isEmpty());
        assertTrue(AgentJson.asList(null).isEmpty());
    }

    @Test
    void strCoercion() {
        assertEquals("", AgentJson.str(null));
        assertEquals("42", AgentJson.str(42));
        assertEquals("true", AgentJson.str(true));
    }

    @Test
    void nonBlankStringStripsOrReturnsNull() {
        assertEquals("x", AgentJson.nonBlankString("  x  "));
        assertNull(AgentJson.nonBlankString("   "));
        assertNull(AgentJson.nonBlankString(""));
        assertNull(AgentJson.nonBlankString(42));
        assertNull(AgentJson.nonBlankString(null));
    }

    @Test
    void logWithNullHelperNeverThrows() {
        assertDoesNotThrow(() -> AgentJson.log(null, "message"));
        assertDoesNotThrow(() -> AgentJson.log(null, null));
    }

    @Test
    void safeJsonReturnsSerializableInput() {
        Map<String, Object> m = Map.of("a", 1);
        assertSame(m, AgentJson.safeJson(m));
    }

    @Test
    void sanitizeForJsonRecursesAndStringifiesUnknowns() {
        assertNull(AgentJson.sanitizeForJson(null));
        assertEquals("s", AgentJson.sanitizeForJson("s"));
        assertEquals(1, AgentJson.sanitizeForJson(1));
        assertEquals(true, AgentJson.sanitizeForJson(true));
        assertEquals("sb", AgentJson.sanitizeForJson(new StringBuilder("sb")));

        Map<Object, Object> mixedKeys = new LinkedHashMap<>();
        mixedKeys.put(1, "x");
        mixedKeys.put("nested", Map.of("b", new StringBuilder("y")));
        Object out = AgentJson.sanitizeForJson(mixedKeys);
        assertEquals(Map.of("1", "x", "nested", Map.of("b", "y")), out);

        assertEquals(List.of("a", "sb"), AgentJson.sanitizeForJson(List.of("a", new StringBuilder("sb"))));
    }

    @Test
    void extractTextFromContentHandlesScalars() {
        assertEquals("", AgentJson.extractTextFromContent(null));
        assertEquals("x", AgentJson.extractTextFromContent("  x  "));
        assertEquals("42", AgentJson.extractTextFromContent(42));
        assertEquals("", AgentJson.extractTextFromContent(Map.of("text", "   ")));
    }

    @Test
    void extractTextFromContentWalksEnvelopesAndLists() {
        assertEquals("hello", AgentJson.extractTextFromContent(Map.of("text", "hello")));
        assertEquals("deep", AgentJson.extractTextFromContent(
                Map.of("content", Map.of("value", "deep"))));
        assertEquals("a\nb\nc", AgentJson.extractTextFromContent(
                List.of(Map.of("type", "text", "text", "a"), Map.of("text", "b"), "c")));
        assertEquals("found", AgentJson.extractTextFromContent(
                Map.of("other", Map.of("text", "found"))));
    }

    @Test
    void extractTextFromContentHonorsDepthCap() {
        Object nested = "x";
        for (int i = 0; i < 3; i++) {
            nested = Map.of("content", nested);
        }
        assertEquals("x", AgentJson.extractTextFromContent(nested));

        Object tooDeep = "x";
        for (int i = 0; i < 11; i++) {
            tooDeep = Map.of("content", tooDeep);
        }
        assertEquals("", AgentJson.extractTextFromContent(tooDeep));
    }

    @Test
    void maybeParseStringParsesWrappedJson() {
        assertEquals(Map.of("a", 1), AgentJson.maybeParseString("  {\"a\":1}  "));
        assertEquals(List.of(1, 2), AgentJson.maybeParseString("[1,2]"));
        assertEquals(Map.of(), AgentJson.maybeParseString("{}"));
    }

    @Test
    void maybeParseStringExtractsResultMarkerTail() {
        assertEquals(Map.of("a", 1),
                AgentJson.maybeParseString("portValue:llm_output:::result:{\"a\":1}"));
        String unparseableTail = "x :::result: not json";
        assertEquals(unparseableTail, AgentJson.maybeParseString(unparseableTail));
    }

    @Test
    void maybeParseStringExtractsEmbeddedObjectFromProse() {
        assertEquals(Map.of("a", 1), AgentJson.maybeParseString("log line {\"a\":1}"));
        assertEquals(List.of(1), AgentJson.maybeParseString("prefix [1]"));
    }

    @Test
    void maybeParseStringLeavesNonJsonUntouched() {
        assertEquals("plain text", AgentJson.maybeParseString("plain text"));
        assertEquals("42", AgentJson.maybeParseString("42"));
        String trailing = "log {\"a\":1} trailing";
        assertEquals(trailing, AgentJson.maybeParseString(trailing));
        // LLM-style code fences are unwrapped and parsed
        assertEquals(Map.of("a", 1), AgentJson.maybeParseString("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1", AgentJson.maybeParseString("{\"a\":1"));
        assertEquals(42, AgentJson.maybeParseString(42));
    }

    @Test
    void unwrapListEnvelopePicksFirstMeaningfulEntry() {
        assertEquals(Map.of(), AgentJson.unwrapListEnvelope(List.of()));
        assertEquals("a", AgentJson.unwrapListEnvelope(List.of(List.of("a"))));
        assertEquals(Map.of("k", 1),
                AgentJson.unwrapListEnvelope(
                        java.util.Arrays.asList(null, Map.of(), List.of(), "  ", Map.of("k", 1))));
        assertNull(AgentJson.unwrapListEnvelope(java.util.Collections.singletonList(null)));
        assertEquals("x", AgentJson.unwrapListEnvelope("x"));
    }

    @Test
    void unwrapSingleItemEnvelopeSemantics() {
        assertEquals("x", AgentJson.unwrapSingleItemEnvelope(List.of("x")));
        assertEquals("x", AgentJson.unwrapSingleItemEnvelope(List.of(List.of("x"))));
        assertEquals(List.of("a", "b"), AgentJson.unwrapSingleItemEnvelope(List.of("a", "b")));
        assertEquals(Map.of(), AgentJson.unwrapSingleItemEnvelope(List.of()));
        assertEquals(Map.of(), AgentJson.unwrapSingleItemEnvelope(java.util.Collections.singletonList(null)));
        assertEquals("y", AgentJson.unwrapSingleItemEnvelope(java.util.Arrays.asList(null, "y")));
        assertEquals("z", AgentJson.unwrapSingleItemEnvelope("z"));
    }

    @Test
    void coerceFilesListHandlesNullAndUnparseable() {
        assertTrue(AgentJson.coerceFilesList(null).isEmpty());
        assertTrue(AgentJson.coerceFilesList("not json").isEmpty());
        assertTrue(AgentJson.coerceFilesList(42).isEmpty());
        assertTrue(AgentJson.coerceFilesList(List.of(42)).isEmpty());
    }

    @Test
    void coerceFilesListDropsPayloadLessEntries() {
        // The duckbot create-node probe's "no attachment" shape: files:[{}], voices:[""].
        // These must NEVER reach the model wire as empty-data blocks (providers reject or hang).
        AgentJson.CoerceResult res = AgentJson.coerceFilesListDetailed(List.of(
                Map.of(),
                "",
                "   ",
                Map.of("name", "a.png"),
                Map.of("kind", "image", "data", ""),
                Map.of("kind", "audio", "data", "def", "name", "v.wav")));
        assertEquals(1, res.rows().size());
        assertEquals("v.wav", res.rows().get(0).get("name"));
        assertEquals(5, res.dropped());

        // Handle-bearing rows survive even without inline data (the model port resolves them).
        AgentJson.CoerceResult handles = AgentJson.coerceFilesListDetailed(List.of(
                Map.of("kind", "image", "source", "sq-file", "name", "p.png", "fileId", "sq-file:abc")));
        assertEquals(1, handles.rows().size());
        assertEquals(0, handles.dropped());

        assertTrue(AgentJson.coerceFilesList(List.of(Map.of())).isEmpty());
        assertTrue(AgentJson.coerceFilesList(List.of("")).isEmpty());
    }

    @Test
    void coerceFilesListWrapsUrlStrings() {
        List<Map<String, Object>> out = AgentJson.coerceFilesList(List.of("http://x/f.pdf"));
        assertEquals(1, out.size());
        assertEquals(Map.of("kind", "document",
                "mime_type", "application/octet-stream",
                "source", "url",
                "data", "http://x/f.pdf",
                "name", ""), out.get(0));
    }

    @Test
    void coerceFilesListNormalizesKindsSourcesAndAliases() {
        List<Map<String, Object>> out = AgentJson.coerceFilesList(List.of(
                Map.of("kind", "IMAGE", "data", "abc", "source", "BASE64", "mimeType", "image/png", "name", "p.png"),
                Map.of("type", "image", "data", "d2"),
                Map.of("kind", "file", "data", "d3"),
                Map.of("kind", "weird", "data", "d4", "source", "ftp")));
        assertEquals(4, out.size());
        assertEquals("image", out.get(0).get("kind"));
        assertEquals("base64", out.get(0).get("source"));
        assertEquals("image/png", out.get(0).get("mime_type"));
        assertEquals("p.png", out.get(0).get("name"));
        assertEquals("image", out.get(1).get("kind"));
        assertEquals("application/octet-stream", out.get(1).get("mime_type"));
        assertEquals("document", out.get(2).get("kind"));
        assertEquals("document", out.get(3).get("kind"));
        assertEquals("base64", out.get(3).get("source"));
    }

    @Test
    void coerceFilesListDataFallbackChainAndSingleMapWrap() {
        List<Map<String, Object>> fromContent = AgentJson.coerceFilesList(List.of(Map.of("content", "c")));
        assertEquals("c", fromContent.get(0).get("data"));
        List<Map<String, Object>> fromUrl = AgentJson.coerceFilesList(List.of(Map.of("url", "u")));
        assertEquals("u", fromUrl.get(0).get("data"));
        List<Map<String, Object>> single = AgentJson.coerceFilesList(Map.of("data", "x"));
        assertEquals(1, single.size());
        List<Map<String, Object>> parsed = AgentJson.coerceFilesList("[{\"kind\":\"image\",\"data\":\"x\"}]");
        assertEquals("image", parsed.get(0).get("kind"));
    }

    @Test
    void filesToContentBlocksMapsKindToType() {
        assertTrue(AgentJson.filesToContentBlocks(null).isEmpty());
        List<Map<String, Object>> blocks = AgentJson.filesToContentBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "base64", "data", "x", "name", "p.png")));
        assertEquals(Map.of("type", "image", "mime_type", "image/png", "source", "base64",
                "data", "x", "name", "p.png"), blocks.get(0));
    }

    @Test
    void coerceFilesListPreservesFileIdAndSqFileSource() {
        List<Map<String, Object>> duckbot = AgentJson.coerceFilesList(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "sq-file",
                        "name", "p.png", "fileId", "sq-file:abc")));
        assertEquals(1, duckbot.size());
        assertEquals("sq-file", duckbot.get(0).get("source"));
        assertEquals("sq-file:abc", duckbot.get(0).get("fileId"));
        assertEquals("p.png", duckbot.get(0).get("name"));
        assertEquals("image/png", duckbot.get(0).get("mime_type"));
        assertFalse(duckbot.get(0).containsKey("data"));

        List<Map<String, Object>> prefixed = AgentJson.coerceFilesList(List.of(
                Map.of("kind", "document", "mime_type", "application/pdf",
                        "source", "sq-file:xyz", "name", "d.pdf")));
        assertEquals("sq-file:xyz", prefixed.get(0).get("source"));
        assertEquals("sq-file:xyz", prefixed.get(0).get("fileId"));
        assertFalse(prefixed.get(0).containsKey("data"));

        List<Map<String, Object>> unknown = AgentJson.coerceFilesList(List.of(
                Map.of("kind", "document", "data", "d4", "source", "ftp")));
        assertEquals("base64", unknown.get(0).get("source"));
        assertEquals("d4", unknown.get(0).get("data"));
        assertFalse(unknown.get(0).containsKey("fileId"));
    }

    @Test
    void filesToContentBlocksPreservesFileIdAndOmitsDataForHandles() {
        List<Map<String, Object>> handle = AgentJson.filesToContentBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "sq-file",
                        "name", "p.png", "fileId", "sq-file:abc")));
        assertEquals("image", handle.get(0).get("type"));
        assertEquals("sq-file:abc", handle.get(0).get("fileId"));
        assertEquals("sq-file", handle.get(0).get("source"));
        assertEquals("p.png", handle.get(0).get("name"));
        assertFalse(handle.get(0).containsKey("data"));

        List<Map<String, Object>> colon = AgentJson.filesToContentBlocks(List.of(
                Map.of("kind", "image", "mime_type", "image/png", "source", "sq-file:abc",
                        "name", "p.png")));
        assertEquals("sq-file:abc", colon.get(0).get("source"));
        assertFalse(colon.get(0).containsKey("data"));
        assertFalse(colon.get(0).containsKey("fileId"));
    }

    @Test
    void compactLookupTextStripsSuffixesAndNonAlnum() {
        assertEquals("myfile", AgentJson.compactLookupText("My File.MD"));
        assertEquals("toolv2", AgentJson.compactLookupText("tool-v2.json"));
        assertEquals("", AgentJson.compactLookupText(null));
    }

    @Test
    void snakeAliasNormalizes() {
        assertEquals("my_tool", AgentJson.snakeAlias("My  Tool!"));
        assertEquals("a_b", AgentJson.snakeAlias("  a--b  "));
        assertEquals("", AgentJson.snakeAlias(null));
    }

    @Test
    void lookupTokensSplitSnakeAlias() {
        assertEquals(List.of("my", "tool"), AgentJson.lookupTokens("My Tool"));
        assertTrue(AgentJson.lookupTokens("  ").isEmpty());
    }

    @Test
    void collectConnectionTextCollectsUsefulKeysRecursively() {
        Map<String, Object> value = Map.of(
                "name", "Tool",
                "secret", "skipped",
                "config", Map.of("label", "L"),
                "list", List.of(Map.of("title", "T"), "bare"));
        List<String> out = AgentJson.collectConnectionText(value, 0);
        assertTrue(out.contains("Tool"));
        assertTrue(out.contains("L"));
        assertTrue(out.contains("T"));
        assertTrue(out.contains("bare"));
        assertTrue(out.stream().noneMatch(s -> s.equals("skipped")));
    }

    @Test
    void canonicalJsonSortsKeysRecursively() {
        assertEquals("{\"a\":2,\"b\":1}", AgentJson.canonicalJson(Map.of("b", 1, "a", 2)));
        assertEquals("[{\"a\":1,\"b\":2}]", AgentJson.canonicalJson(List.of(Map.of("b", 2, "a", 1))));
    }

    @Test
    void jsonStringWritesCompactJson() {
        assertEquals("{\"a\":1}", AgentJson.jsonString(Map.of("a", 1)));
    }

    @Test
    void takeNonemptyStrPicksFirstNonBlank() {
        assertEquals("x", AgentJson.takeNonemptyStr(null, " ", " x "));
        assertNull(AgentJson.takeNonemptyStr("", null, "  "));
        assertNull(AgentJson.takeNonemptyStr((Object[]) null));
        assertNull(AgentJson.takeNonemptyStr());
    }

    @Test
    void mediaBlockKindsAreFixed() {
        assertEquals(java.util.Set.of("image", "audio", "video", "document"), AgentJson.MEDIA_BLOCK_KINDS);
    }
}
