package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Date & Time node: now / add / subtract / format / extract / diff / round. */
class DateTimeNodeTest {

    private final DateTimeNode node = new DateTimeNode();

    private static Map<String, Object> requestWithConfig(Object... shortCodeThenValue) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i + 1 < shortCodeThenValue.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", shortCodeThenValue[i]);
            row.put("value", shortCodeThenValue[i + 1]);
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        return request;
    }

    private static Map<String, Object> withInput(Map<String, Object> request, Object inputValue) {
        request.put("inputVariables", Map.of("input", inputValue));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("output");
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> dataOf(Map<String, Object> ports) {
        assertEquals("ok", output(ports).get("status"), "expected ok: " + ports);
        return (Map<String, Object>) output(ports).get("data");
    }

    @Test
    void nowReturnsAParseableIsoInstant() {
        Map<String, Object> request = withInput(requestWithConfig("operation", "now"), Map.of());
        Object datetime = dataOf(node.handle(request, null)).get("datetime");
        assertTrue(datetime instanceof String, "datetime must be a string: " + datetime);
        assertDoesNotThrow(() -> OffsetDateTime.parse((String) datetime));
    }

    @Test
    void addDays() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "add", "unit", "days"),
                Map.of("datetime", "2026-01-01T00:00:00Z", "amount", 5));
        assertEquals("2026-01-06T00:00:00Z", dataOf(node.handle(request, null)).get("datetime"));
    }

    @Test
    void subtractMonths() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "subtract", "unit", "months"),
                Map.of("datetime", "2026-03-15T12:00:00Z", "amount", 1));
        assertEquals("2026-02-15T12:00:00Z", dataOf(node.handle(request, null)).get("datetime"));
    }

    @Test
    void addDefaultsToNowWhenDatetimeIsMissing() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "add", "unit", "days"),
                Map.of("amount", 0));
        Object datetime = dataOf(node.handle(request, null)).get("datetime");
        assertDoesNotThrow(() -> OffsetDateTime.parse((String) datetime));
    }

    @Test
    void formatWithCustomPattern() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format", "format", "yyyy/MM/dd"),
                Map.of("datetime", "2026-01-05T10:30:00Z"));
        assertEquals("2026/01/05", dataOf(node.handle(request, null)).get("datetime"));
    }

    @Test
    void extractParts() {
        // 2026-01-01 is a Thursday → ISO dow 4
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "extract"),
                Map.of("datetime", "2026-01-01T10:20:30Z", "part", "dow"));
        assertEquals(Map.of("part", "dow", "value", 4L), dataOf(node.handle(request, null)));
    }

    @Test
    void diffInWholeDays() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "diff", "unit", "days"),
                Map.of("datetime", "2026-01-10T00:00:00Z", "other", "2026-01-01T00:00:00Z"));
        assertEquals(Map.of("difference", 9L, "unit", "days"), dataOf(node.handle(request, null)));
    }

    @Test
    void roundFloorsToMonthStart() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "round", "unit", "months"),
                Map.of("datetime", "2026-01-15T10:30:00Z"));
        assertEquals("2026-01-01T00:00:00Z", dataOf(node.handle(request, null)).get("datetime"));
    }

    @Test
    void roundWeekStartsOnMonday() {
        // 2026-01-04 is a Sunday → floor to Monday 2025-12-29
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "round", "unit", "weeks"),
                Map.of("datetime", "2026-01-04T10:30:00Z"));
        assertEquals("2025-12-29T00:00:00Z", dataOf(node.handle(request, null)).get("datetime"));
    }

    @Test
    void unknownTimezoneIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "now", "timezone", "Not/AZone"),
                Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("timezone"));
    }

    @Test
    void unknownUnitIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "add", "unit", "fortnights"),
                Map.of("datetime", "2026-01-01T00:00:00Z", "amount", 1));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unit"));
    }

    @Test
    void missingDatetimeForFormatIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("datetime"));
    }

    @Test
    void malformedDatetimeIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "format"),
                Map.of("datetime", "not a date"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("datetime"));
    }

    @Test
    void unknownPartIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "extract"),
                Map.of("datetime", "2026-01-01T00:00:00Z", "part", "century"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("part"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "parse"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
