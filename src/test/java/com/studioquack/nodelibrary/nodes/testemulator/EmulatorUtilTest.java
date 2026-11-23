package com.studioquack.nodelibrary.nodes.testemulator;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure coercion helpers — no I/O, no process. */
class EmulatorUtilTest {

    // ---------- env ----------

    @Test
    void envReturnsDefaultForUnsetKey() {
        assertEquals("fallback", EmulatorUtil.env("SQ_TEST_DEFINITELY_UNSET_9f3b2c", "fallback"));
    }

    @Test
    void envReturnsNullDefaultWhenUnsetAndDefaultNull() {
        assertNull(EmulatorUtil.env("SQ_TEST_DEFINITELY_UNSET_9f3b2c", null));
    }

    // ---------- str ----------

    @Test
    void strNullBecomesEmpty() {
        assertEquals("", EmulatorUtil.str(null));
    }

    @Test
    void strStringifiesNumbersAndObjects() {
        assertEquals("42", EmulatorUtil.str(42));
        assertEquals("3.5", EmulatorUtil.str(3.5d));
        assertEquals("true", EmulatorUtil.str(Boolean.TRUE));
    }

    // ---------- asMap ----------

    @Test
    void asMapReturnsMapWhenMap() {
        Map<String, Object> in = Map.of("a", 1);
        assertEquals(in, EmulatorUtil.asMap(in));
    }

    @Test
    void asMapReturnsEmptyForNonMaps() {
        assertTrue(EmulatorUtil.asMap(null).isEmpty());
        assertTrue(EmulatorUtil.asMap("x").isEmpty());
        assertTrue(EmulatorUtil.asMap(List.of(1)).isEmpty());
        assertTrue(EmulatorUtil.asMap(7).isEmpty());
    }

    // ---------- mutableMap ----------

    @Test
    void mutableMapIsAMutableCopy() {
        Map<String, Object> src = new LinkedHashMap<>();
        src.put("a", 1);
        Map<String, Object> copy = EmulatorUtil.mutableMap(src);
        copy.put("b", 2);
        assertEquals(2, copy.size());
        assertEquals(1, src.size(), "source map must not be affected");
    }

    @Test
    void mutableMapOfNonMapIsEmptyAndMutable() {
        Map<String, Object> m = EmulatorUtil.mutableMap("nope");
        assertTrue(m.isEmpty());
        m.put("k", "v");
        assertEquals("v", m.get("k"));
    }

    // ---------- asList ----------

    @Test
    void asListReturnsListWhenList() {
        List<Object> in = List.of(1, 2);
        assertEquals(in, EmulatorUtil.asList(in));
    }

    @Test
    void asListReturnsEmptyForNonLists() {
        assertTrue(EmulatorUtil.asList(null).isEmpty());
        assertTrue(EmulatorUtil.asList(Map.of()).isEmpty());
        assertTrue(EmulatorUtil.asList("x").isEmpty());
    }

    // ---------- parseInt ----------

    @ParameterizedTest
    @CsvSource({
            "'7', 0, 7",
            "' 42 ', 0, 42",
            "'-3', 0, -3",
            "'abc', 9, 9",
            "'', 9, 9",
            "'  ', 9, 9",
            "'3.5', 9, 9"
    })
    void parseIntVariants(String s, int dflt, int expected) {
        assertEquals(expected, EmulatorUtil.parseInt(s, dflt));
    }

    @Test
    void parseIntNullReturnsDefault() {
        assertEquals(5, EmulatorUtil.parseInt(null, 5));
    }

    // ---------- toInt ----------

    @Test
    void toIntFromNumbers() {
        assertEquals(3, EmulatorUtil.toInt(3, 0));
        assertEquals(3, EmulatorUtil.toInt(3.9d, 0), "double truncates via intValue");
        assertEquals(-2, EmulatorUtil.toInt(-2L, 0));
    }

    @Test
    void toIntFromStrings() {
        assertEquals(12, EmulatorUtil.toInt("12", 0));
        assertEquals(12, EmulatorUtil.toInt(" 12 ", 0));
        assertEquals(8, EmulatorUtil.toInt("12x", 8));
        assertEquals(8, EmulatorUtil.toInt("", 8));
    }

    @Test
    void toIntFromOtherTypesReturnsDefault() {
        assertEquals(4, EmulatorUtil.toInt(null, 4));
        assertEquals(4, EmulatorUtil.toInt(Boolean.TRUE, 4));
        assertEquals(4, EmulatorUtil.toInt(List.of(1), 4));
    }

    // ---------- isTruthy ----------

    @ParameterizedTest
    @ValueSource(strings = {"1", "true", "TRUE", "True", "yes", "YES", " yes "})
    void isTruthyTrueStrings(String s) {
        assertTrue(EmulatorUtil.isTruthy(s));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "false", "no", "off", "2", "", "  "})
    void isTruthyFalseStrings(String s) {
        assertFalse(EmulatorUtil.isTruthy(s));
    }

    @Test
    void isTruthyBooleansAndNull() {
        assertTrue(EmulatorUtil.isTruthy(Boolean.TRUE));
        assertFalse(EmulatorUtil.isTruthy(Boolean.FALSE));
        assertFalse(EmulatorUtil.isTruthy(null));
    }

    @Test
    void isTruthyNumbersUseStringForm() {
        assertTrue(EmulatorUtil.isTruthy(1), "Integer 1 stringifies to \"1\"");
        assertFalse(EmulatorUtil.isTruthy(0));
    }

    // ---------- lower ----------

    @Test
    void lowerTrimsAndLowercases() {
        assertEquals("abc", EmulatorUtil.lower("  AbC "));
        assertEquals("", EmulatorUtil.lower(null));
        assertEquals("", EmulatorUtil.lower("   "));
    }

    // ---------- truncate ----------

    @Test
    void truncateNullBecomesEmpty() {
        assertEquals("", EmulatorUtil.truncate(null, 10));
    }

    @Test
    void truncateNoCapWhenMaxCharsNonPositive() {
        String longText = "x".repeat(500);
        assertSame(longText, EmulatorUtil.truncate(longText, 0));
        assertSame(longText, EmulatorUtil.truncate(longText, -5));
    }

    @Test
    void truncateShorterThanCapUnchanged() {
        assertEquals("abc", EmulatorUtil.truncate("abc", 10));
        assertEquals("abc", EmulatorUtil.truncate("abc", 3), "exactly at cap is unchanged");
    }

    @Test
    void truncateAppendsMarker() {
        String out = EmulatorUtil.truncate("abcdefghij", 4);
        assertTrue(out.startsWith("abcd"));
        assertTrue(out.contains("[truncated, max_log_chars=4]"), out);
    }

    // ---------- dig ----------

    @Test
    void digWalksNestedMaps() {
        Map<String, Object> root = Map.of("a", Map.of("b", Map.of("c", 99)));
        assertEquals(99, EmulatorUtil.dig(root, "a", "b", "c"));
    }

    @Test
    void digMissingKeyReturnsNull() {
        Map<String, Object> root = Map.of("a", Map.of("b", 1));
        assertNull(EmulatorUtil.dig(root, "a", "zzz"));
    }

    @Test
    void digNonMapHopReturnsNull() {
        Map<String, Object> root = Map.of("a", List.of(1, 2));
        assertNull(EmulatorUtil.dig(root, "a", "b"));
    }

    @Test
    void digNullRootReturnsNull() {
        assertNull(EmulatorUtil.dig(null, "a"));
    }

    @Test
    void digWithNoKeysReturnsRoot() {
        Map<String, Object> root = Map.of("a", 1);
        assertSame(root, EmulatorUtil.dig(root));
    }

    @Test
    void digNullValueAtLeafReturnsNull() {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("b", null);
        Map<String, Object> root = Map.of("a", inner);
        assertNull(EmulatorUtil.dig(root, "a", "b"));
    }
}
