package com.studioquack.nodelibrary.nodes.testemulator;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deep-compare semantics: strict key sets, array lengths, number normalisation. */
class OutputComparerTest {

    // ---------- null expected = no assertion ----------

    @Test
    void nullExpectedAlwaysMatches() {
        assertTrue(OutputComparer.compare(Map.of("a", 1), null).matches());
        assertTrue(OutputComparer.compare(null, null).matches());
        assertTrue(OutputComparer.compare("anything", null).differences().isEmpty());
    }

    // ---------- scalars ----------

    @Test
    void equalScalarsMatch() {
        assertTrue(OutputComparer.compare("x", "x").matches());
        assertTrue(OutputComparer.compare(true, true).matches());
        assertTrue(OutputComparer.compare(5L, 5L).matches());
    }

    @Test
    void integerAndDoubleWithSameValueMatch() {
        assertTrue(OutputComparer.compare(1L, 1.0d).matches(),
                "JSON round-trips make 1 arrive as Long and 1.0 as Double");
        assertTrue(OutputComparer.compare(2.0d, 2L).matches());
    }

    @Test
    void differentNumericValuesMismatch() {
        OutputComparer.CompareResult r = OutputComparer.compare(1L, 1.5d);
        assertFalse(r.matches());
        assertEquals(List.of("Value mismatch at root: expected 1.5, got 1"), r.differences());
    }

    @Test
    void scalarTypeMismatchReported() {
        OutputComparer.CompareResult r = OutputComparer.compare("1", 1L);
        assertFalse(r.matches());
        assertEquals(List.of("Type mismatch at root: expected number, got string"), r.differences());
    }

    @Test
    void nullExpectedLeafVersusValueIsTypeMismatch() {
        Map<String, Object> expected = new java.util.LinkedHashMap<>();
        expected.put("a", null);
        OutputComparer.CompareResult r = OutputComparer.compare(Map.of("a", 1L), expected);
        assertFalse(r.matches());
        assertEquals(List.of("Type mismatch at root.a: expected null, got number"), r.differences());
    }

    @Test
    void nullLeavesOnBothSidesMatch() {
        Map<String, Object> a = new java.util.LinkedHashMap<>();
        a.put("x", null);
        Map<String, Object> b = new java.util.LinkedHashMap<>();
        b.put("x", null);
        assertTrue(OutputComparer.compare(a, b).matches());
    }

    // ---------- objects ----------

    @Test
    void equalMapsMatch() {
        assertTrue(OutputComparer.compare(
                Map.of("a", 1L, "b", Map.of("c", "x")),
                Map.of("a", 1L, "b", Map.of("c", "x"))).matches());
    }

    @Test
    void missingKeyReported() {
        OutputComparer.CompareResult r = OutputComparer.compare(Map.of(), Map.of("a", 1L));
        assertFalse(r.matches());
        assertEquals(List.of("Missing key at root.a"), r.differences());
    }

    @Test
    void unexpectedKeyReported() {
        OutputComparer.CompareResult r = OutputComparer.compare(Map.of("a", 1L), Map.of());
        assertFalse(r.matches());
        assertEquals(List.of("Unexpected key at root.a"), r.differences());
    }

    @Test
    void nestedValueMismatchCarriesFullPath() {
        OutputComparer.CompareResult r = OutputComparer.compare(
                Map.of("outer", Map.of("inner", "actual")),
                Map.of("outer", Map.of("inner", "expected")));
        assertFalse(r.matches());
        assertEquals(List.of("Value mismatch at root.outer.inner: expected expected, got actual"),
                r.differences());
    }

    @Test
    void mapVersusArrayIsTypeMismatch() {
        OutputComparer.CompareResult r = OutputComparer.compare(List.of(1L), Map.of("a", 1L));
        assertFalse(r.matches());
        assertEquals(List.of("Type mismatch at root: expected object, got array"), r.differences());
    }

    @Test
    void expectedMapVersusScalarActual() {
        OutputComparer.CompareResult r = OutputComparer.compare("x", Map.of("a", 1L));
        assertFalse(r.matches());
        assertEquals(List.of("Type mismatch at root: expected object, got string"), r.differences());
    }

    // ---------- arrays ----------

    @Test
    void equalArraysMatch() {
        assertTrue(OutputComparer.compare(List.of(1L, "a"), List.of(1.0d, "a")).matches());
    }

    @Test
    void arrayLengthMismatchReported() {
        OutputComparer.CompareResult r = OutputComparer.compare(List.of(1L), List.of(1L, 2L));
        assertFalse(r.matches());
        assertEquals(List.of("Array length mismatch at root: expected 2, got 1"), r.differences());
    }

    @Test
    void arrayElementMismatchCarriesIndexPath() {
        OutputComparer.CompareResult r = OutputComparer.compare(List.of("a", "b"), List.of("a", "c"));
        assertFalse(r.matches());
        assertEquals(List.of("Value mismatch at root[1]: expected c, got b"), r.differences());
    }

    @Test
    void expectedArrayVersusScalarActual() {
        OutputComparer.CompareResult r = OutputComparer.compare(7L, List.of(7L));
        assertFalse(r.matches());
        assertEquals(List.of("Type mismatch at root: expected array, got number"), r.differences());
    }

    @Test
    void nestedArrayInsideObjectPath() {
        OutputComparer.CompareResult r = OutputComparer.compare(
                Map.of("items", List.of(Map.of("id", 1L))),
                Map.of("items", List.of(Map.of("id", 2L))));
        assertFalse(r.matches());
        assertEquals(List.of("Value mismatch at root.items[0].id: expected 2, got 1"), r.differences());
    }

    // ---------- multiple differences accumulate ----------

    @Test
    void missingAndUnexpectedAndValueDiffsAccumulate() {
        OutputComparer.CompareResult r = OutputComparer.compare(
                Map.of("keep", "a", "extra", 1L),
                Map.of("keep", "b", "want", 2L));
        assertFalse(r.matches());
        assertEquals(3, r.differences().size());
        assertTrue(r.differences().contains("Missing key at root.want"));
        assertTrue(r.differences().contains("Unexpected key at root.extra"));
        assertTrue(r.differences().contains("Value mismatch at root.keep: expected b, got a"));
    }

    @Test
    void booleanMismatchReported() {
        OutputComparer.CompareResult r = OutputComparer.compare(false, true);
        assertFalse(r.matches());
        assertEquals(List.of("Value mismatch at root: expected true, got false"), r.differences());
    }
}
