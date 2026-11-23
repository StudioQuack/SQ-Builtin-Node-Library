package com.studioquack.nodelibrary.nodes.datastore;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PgVectorNodeTest {

    private static Map<String, Object> cfg() {
        return Map.of(
                "schema", "public",
                "table", "documents",
                "id_column", "id",
                "document_column", "document",
                "embedding_column", "embedding",
                "metadata_column", "metadata");
    }

    @Test
    void queryWithEmbeddingUsesCosineDistance() {
        String sql = PgVectorNode.buildQuerySql(cfg(), Map.of(
                "embedding", List.of(0.1, 0.2),
                "topK", 3,
                "filter", Map.of("source", "orders")));
        assertTrue(sql.contains("<=> '[0.1,0.2]'::vector"));
        assertTrue(sql.contains("LIMIT 3"));
        assertTrue(sql.contains("\"metadata\" ->> $sq$source$sq$ = $sq$orders$sq$"));
        assertTrue(sql.startsWith("SELECT \"id\", \"document\""));
    }

    @Test
    void queryWithoutEmbeddingUsesPositionFallback() {
        String sql = PgVectorNode.buildQuerySql(cfg(), Map.of("query", "order 42"));
        assertTrue(sql.contains("position($sq$order 42$sq$ in \"document\") > 0"));
        assertTrue(sql.contains("LIMIT 5"));
        assertTrue(!sql.contains("<=>"));
    }

    @Test
    void upsertAndDeleteQuoteIdentifiers() {
        String upsert = PgVectorNode.buildUpsertSql(cfg(), Map.of(
                "id", "42",
                "document", "bin A",
                "embedding", List.of(1.0, 2.0),
                "metadata", Map.of("source", "w")));
        assertTrue(upsert.startsWith("INSERT INTO \"public\".\"documents\""));
        assertTrue(upsert.contains("ON CONFLICT (\"id\")"));
        String del = PgVectorNode.buildDeleteSql(cfg(), Map.of("id", "42"));
        assertTrue(del.contains("DELETE FROM \"public\".\"documents\" WHERE \"id\" = $sq$42$sq$"));
    }

    @Test
    void rejectBadIdentifierAndDollarProbe() {
        assertThrows(IllegalArgumentException.class, () -> PgVectorNode.quoteIdent("x;drop"));
        assertThrows(IllegalArgumentException.class, () -> PgVectorNode.lit("evil$sq$"));
        assertThrows(IllegalArgumentException.class, () -> PgVectorNode.vectorLiteral(List.of("nan")));
        assertEquals("'[1.0,2.0]'", PgVectorNode.vectorLiteral(List.of(1.0, 2.0)));
    }

    @Test
    void handleFailsClosedWithoutTable() {
        PgVectorNode node = new PgVectorNode();
        Map<String, Object> req = Map.of(
                "inputVariables", Map.of("input", Map.of("operation", "QUERY", "query", "x")),
                "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                        "configurationParameters", List.of(
                                Map.of("shortCode", "host", "value", "127.0.0.1"),
                                Map.of("shortCode", "table", "value", ""))))));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("table is required"));
    }

    @Test
    void unsupportedOperationIsInvalidInput() {
        PgVectorNode node = new PgVectorNode();
        Map<String, Object> req = Map.of(
                "inputVariables", Map.of("input", Map.of("operation", "DROP", "query", "x")),
                "executionContext", Map.of("currentNode", Map.of("data", Map.of(
                        "configurationParameters", List.of(
                                Map.of("shortCode", "table", "value", "documents"))))));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> node.handle(req, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertEquals("unsupported operation", e.getMessage());
    }
}
