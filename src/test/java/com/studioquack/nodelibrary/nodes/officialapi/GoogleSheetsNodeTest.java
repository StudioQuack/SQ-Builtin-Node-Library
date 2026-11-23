package com.studioquack.nodelibrary.nodes.officialapi;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import studioquack.StudioQuackError;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Validation paths only — no network I/O. */
class GoogleSheetsNodeTest {

    private final GoogleSheetsNode node = new GoogleSheetsNode();

    @Test
    void missingServiceAccountIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(Map.of(), Map.of()), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().startsWith("service_account_json parameter is required"));
    }

    @Test
    void invalidSpreadsheetIdIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        Map.of("service_account_json", Map.of("client_email", "a@b.c",
                                        "private_key", "x"),
                                "spreadsheet_id", "short"),
                        Map.of("action", "read", "range", "Sheet1!A1")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().startsWith("Invalid spreadsheet_id:"));
    }

    @Test
    void missingSpreadsheetIdIsInvalidInput() {
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> node.handle(request(
                        Map.of("service_account_json", Map.of("client_email", "a@b.c",
                                "private_key", "x")),
                        Map.of("action", "read")), null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().startsWith("spreadsheet_id is required"), e.getMessage());
    }

    private static Map<String, Object> request(Map<String, Object> cfg, Map<String, Object> input) {
        List<Map<String, Object>> params = new ArrayList<>();
        for (var e : cfg.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", e.getKey());
            row.put("value", e.getValue());
            params.add(row);
        }
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("executionContext", Map.of("currentNode", Map.of("data",
                Map.of("configurationParameters", params))));
        req.put("inputVariables", Map.of("input", input));
        return req;
    }
}
