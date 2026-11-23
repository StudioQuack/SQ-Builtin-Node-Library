package com.studioquack.nodelibrary.nodes.testemulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Round-trip and error paths of the zero-dependency JSON reader/writer. */
class JsonTest {

    // ---------- write ----------

    @Test
    void writeMapProducesCompactJsonInKeyOrder() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("a", 1);
        m.put("b", "x");
        assertEquals("{\"a\":1,\"b\":\"x\"}", Json.write(m));
    }

    @Test
    void writeNestedStructures() {
        // LinkedHashMap: Map.of iteration order is unspecified — order assertions need insertion order
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("list", List.of(1, "two", true));
        m.put("obj", Map.of("n", List.of()));
        assertEquals("{\"list\":[1,\"two\",true],\"obj\":{\"n\":[]}}", Json.write(m));
    }

    @Test
    void writeValueScalars() {
        assertEquals("null", Json.writeValue(null));
        assertEquals("true", Json.writeValue(Boolean.TRUE));
        assertEquals("7", Json.writeValue(7L));
        assertEquals("2.5", Json.writeValue(2.5d));
        assertEquals("\"hi\"", Json.writeValue("hi"));
    }

    @Test
    void writeEscapesSpecialCharacters() {
        assertEquals("\"a\\\"b\"", Json.writeValue("a\"b"));
        assertEquals("\"a\\\\b\"", Json.writeValue("a\\b"));
        assertEquals("\"a\\nb\"", Json.writeValue("a\nb"));
        assertEquals("\"a\\tb\"", Json.writeValue("a\tb"));
        assertEquals("\"a\\rb\"", Json.writeValue("a\rb"));
        assertEquals("\"\\u0001\"", Json.writeValue("\u0001"), "control chars become \\u escapes");
    }

    @Test
    void writeUnknownTypeIsStringified() {
        assertEquals("\"x\"", Json.writeValue(new StringBuilder("x")));
    }

    // ---------- parse ----------

    @Test
    void parseIntegersBecomeLongAndDecimalsDouble() {
        assertEquals(1L, Json.parse("1"));
        assertEquals(-2L, Json.parse("-2"));
        assertInstanceOf(Long.class, Json.parse("42"));
        assertEquals(1.5d, Json.parse("1.5"));
        assertInstanceOf(Double.class, Json.parse("1e3"));
        assertEquals(1000.0d, (Double) Json.parse("1e3"), 0.0001);
        assertEquals(-2.5d, (Double) Json.parse("-2.5"), 0.0001);
    }

    @Test
    void parseLiterals() {
        assertEquals(Boolean.TRUE, Json.parse("true"));
        assertEquals(Boolean.FALSE, Json.parse("false"));
        assertNull(Json.parse("null"));
    }

    @Test
    void parseStringWithEscapes() {
        assertEquals("a\nb", Json.parse("\"a\\nb\""));
        assertEquals("A", Json.parse("\"\\u0041\""));
        assertEquals("a\"b", Json.parse("\"a\\\"b\""));
        assertEquals("a/b", Json.parse("\"a\\/b\""));
    }

    @Test
    void parseToleratesWhitespace() {
        Object v = Json.parse("  {  \"a\" : [ 1 , 2 ] , \"b\" : null }  ");
        Map<String, Object> m = EmulatorUtil.asMap(v);
        assertEquals(List.of(1L, 2L), m.get("a"));
        assertTrue(m.containsKey("b"));
        assertNull(m.get("b"));
    }

    @Test
    void parsePreservesKeyOrder() {
        Object v = Json.parse("{\"b\":1,\"a\":2}");
        assertEquals(List.of("b", "a"), List.copyOf(EmulatorUtil.asMap(v).keySet()));
    }

    @Test
    void roundTripKeepsValues() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("s", "text");
        m.put("n", 12L);
        m.put("d", 1.25d);
        m.put("b", false);
        m.put("list", List.of(1L, "x"));
        m.put("obj", Map.of("k", "v"));
        Object parsed = Json.parse(Json.write(m));
        assertEquals(m, parsed);
    }

    // ---------- readObject ----------

    @Test
    void readObjectReturnsMapForObjectRoot() {
        assertEquals(Map.of("a", 1L), Json.readObject("{\"a\":1}"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"[1,2]", "\"x\"", "7", "true", "null"})
    void readObjectRejectsNonObjectRoots(String src) {
        assertThrows(IllegalArgumentException.class, () -> Json.readObject(src));
    }

    // ---------- malformed input ----------

    @ParameterizedTest
    @ValueSource(strings = {"", "   ", "{", "[1,", "\"abc", "tru", "nul", "{a:1}", "{\"a\":}", "[1 2]", "@", "{\"a\":1,"})
    void parseMalformedThrowsIllegalArgument(String src) {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(src));
    }

    @Test
    void parseNullThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse(null));
    }

    @Test
    void parseBadEscapeThrows() {
        assertThrows(IllegalArgumentException.class, () -> Json.parse("\"a\\qb\""));
    }
}
