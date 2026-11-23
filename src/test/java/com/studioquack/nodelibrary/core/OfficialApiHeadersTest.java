package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** extraHeaders merge + interpolation, including omission and dynamic dates. */
class OfficialApiHeadersTest {

    private static final Instant NOW = Instant.parse("2015-08-30T12:36:00Z");

    @Test
    void mergeOrderTopThenOpThenStream() {
        OfficialApiSpec spec = OfficialApiSpec.fromMap("t", Map.of(
                "baseUrl", "https://x.test",
                "extraHeaders", Map.of("X-A", "top", "X-B", "top"),
                "operations", Map.of("chat", Map.of(
                        "extraHeaders", Map.of("X-B", "op", "X-C", "op"),
                        "stream", Map.of(
                                "format", "openai-sse",
                                "extraHeaders", Map.of("X-C", "stream", "X-D", "stream"))))));
        Map<String, String> m = OfficialApiHeaders.merge(spec, spec.operation("chat"));
        assertEquals("top", m.get("X-A"));
        assertEquals("op", m.get("X-B"));
        assertEquals("stream", m.get("X-C"));
        assertEquals("stream", m.get("X-D"));
    }

    @Test
    void interpolatesConfigAndInputPlaceholders() {
        Map<String, String> raw = Map.of("X-Key", "k-{api_key}", "X-Region", "{region}");
        Map<String, String> out = OfficialApiHeaders.interpolate(raw,
                Map.of("region", "us-east-1"),
                Map.of("api_key", "sekret"),
                NOW);
        assertEquals("k-sekret", out.get("X-Key"));
        assertEquals("us-east-1", out.get("X-Region"));
    }

    @Test
    void omitsHeaderWhenAnyPlaceholderIsUnresolvable() {
        Map<String, String> raw = new LinkedHashMap<>();
        raw.put("X-Keep", "ok");
        raw.put("X-Drop", "pre-{missing}-post");
        raw.put("X-EmptyKey", "{api_key}");
        Map<String, String> out = OfficialApiHeaders.interpolate(raw, Map.of(), Map.of(), NOW);
        assertEquals("ok", out.get("X-Keep"));
        assertFalse(out.containsKey("X-Drop"));
        assertFalse(out.containsKey("X-EmptyKey"));
    }

    @Test
    void omitsUnknownDynamicPlaceholder() {
        Map<String, String> out = OfficialApiHeaders.interpolate(
                Map.of("X-Now", "{$now:nope}"), Map.of(), Map.of(), NOW);
        assertTrue(out.isEmpty());
    }

    @Test
    void dynamicRfc1123AndIso8601() {
        Map<String, String> raw = Map.of(
                "x-ms-date", "{$now:rfc1123}",
                "X-Iso", "{$now:iso8601}");
        Map<String, String> out = OfficialApiHeaders.interpolate(raw, Map.of(), Map.of(), NOW);
        assertEquals("Sun, 30 Aug 2015 12:36:00 GMT", out.get("x-ms-date"));
        assertEquals("2015-08-30T12:36:00Z", out.get("X-Iso"));
    }

    @Test
    void staticValuesPassThrough() {
        Map<String, String> out = OfficialApiHeaders.interpolate(
                Map.of("anthropic-version", "2023-06-01"), Map.of(), Map.of(), NOW);
        assertEquals("2023-06-01", out.get("anthropic-version"));
    }

    @Test
    void interpolateValueNullTemplate() {
        assertNull(OfficialApiHeaders.interpolateValue(null, Map.of(), Map.of(), NOW));
    }
}
