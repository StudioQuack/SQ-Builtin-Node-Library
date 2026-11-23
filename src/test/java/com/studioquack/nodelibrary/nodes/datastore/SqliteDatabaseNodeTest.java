package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.nodes.datastore.SqlDatabaseNode.Dialect;
import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** SQLite node end to end: the database travels as a file value in and out. */
class SqliteDatabaseNodeTest {

    private final SqlDatabaseNode node = new SqlDatabaseNode(Dialect.SQLITE);

    @SuppressWarnings("unchecked")
    private Map<String, Object> run(Object database, Map<String, Object> input) {
        Map<String, Object> in = new LinkedHashMap<>(input);
        if (database != null) {
            in.put("database", database);
        }
        Map<String, Object> out = node.handle(SqlDatabaseNodeTest.request(new LinkedHashMap<>(), in), null);
        return (Map<String, Object>) out.get("output");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> file(Map<String, Object> output) {
        return (Map<String, Object>) output.get("file");
    }

    @SuppressWarnings("unchecked")
    @Test
    void createInsertQueryRoundTripThroughFileValues() {
        Map<String, Object> created = run(null, Map.of("operation", "execute",
                "query", "CREATE TABLE orders (id INTEGER PRIMARY KEY, customer TEXT, total REAL, raw BLOB)"));
        Map<String, Object> db = file(created);
        assertEquals("file", db.get("type"));
        assertEquals("database.sqlite", db.get("name"));
        assertEquals("application/vnd.sqlite3", db.get("mime_type"));

        Map<String, Object> inserted = run(db, Map.of("operation", "insert", "table", "orders",
                "rows", List.of(Map.of("id", 1, "customer", "acme", "total", 12.5),
                        Map.of("id", 2, "customer", "globex", "total", 99.0))));
        assertEquals(2, ((Number) inserted.get("affected_rows")).intValue());
        db = file(inserted);
        byte[] bytes = Base64.getDecoder().decode((String) db.get("data"));
        assertEquals("SQLite format 3\u0000", new String(bytes, 0, 16, StandardCharsets.US_ASCII));

        Map<String, Object> rows = run(db, Map.of("operation", "query",
                "query", "SELECT id, customer, total, raw FROM orders WHERE total > ? ORDER BY id",
                "parameters", List.of(10)));
        assertNull(rows.get("file"), "query must not return a database file");
        List<Map<String, Object>> list = (List<Map<String, Object>>) rows.get("rows");
        assertEquals(2, list.size());
        assertEquals("acme", list.get(0).get("customer"));
        assertEquals(12.5, ((Number) list.get(0).get("total")).doubleValue());
        assertNull(list.get(0).get("raw"));

        Map<String, Object> injected = run(db, Map.of("operation", "query",
                "query", "SELECT id FROM orders WHERE customer = ?", "parameters", List.of("acme' OR '1'='1")));
        assertEquals(0, ((Number) injected.get("row_count")).intValue());
    }

    @Test
    void queryOperationCannotWrite() {
        Map<String, Object> db = file(run(null, Map.of("operation", "execute",
                "query", "CREATE TABLE t (id INTEGER)")));
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> run(db, Map.of("operation", "query", "query", "INSERT INTO t VALUES (1) RETURNING id")));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("readonly"), e.getMessage());
    }

    @Test
    void attachIsDisabled() {
        Map<String, Object> db = file(run(null, Map.of("operation", "execute",
                "query", "CREATE TABLE t (id INTEGER)")));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> run(db, Map.of("operation", "execute",
                "query", "ATTACH DATABASE '/tmp/sq-attach-probe.db' AS other")));
        assertEquals("REQUEST_FAILED", e.errorCode());
        assertTrue(e.getMessage().contains("too many attached databases"), e.getMessage());
        assertFalse(Files.exists(Path.of("/tmp/sq-attach-probe.db")));
    }

    @Test
    void nonSqliteFileIsInvalidInput() {
        Map<String, Object> notDb = Map.of("type", "file", "name", "notes.txt", "mime_type", "text/plain",
                "data", Base64.getEncoder().encodeToString(
                        "this is plainly not a sqlite database, just text padding the header".getBytes()));
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> run(notDb, Map.of("operation", "query", "query", "SELECT 1")));
        assertEquals("INVALID_INPUT", e.errorCode());
    }

    @Test
    void growthIsCappedAtTheNodeFileLimit() {
        Map<String, Object> db = file(run(null, Map.of("operation", "execute",
                "query", "CREATE TABLE big (b BLOB)")));
        StudioQuackError e = assertThrows(StudioQuackError.class, () -> run(db, Map.of("operation", "execute",
                "query", "WITH RECURSIVE n(i) AS (SELECT 1 UNION ALL SELECT i + 1 FROM n WHERE i < 20) "
                        + "INSERT INTO big SELECT randomblob(1048576) FROM n")));
        assertTrue(e.getMessage().contains("file limit"), e.getMessage());
    }

    @Test
    void tempCopiesAreDeleted() throws Exception {
        Path tmp = Path.of(System.getProperty("java.io.tmpdir"));
        long before = countTemp(tmp);
        Map<String, Object> db = file(run(null, Map.of("operation", "execute", "query", "CREATE TABLE t (id INTEGER)")));
        run(db, Map.of("operation", "query", "query", "SELECT * FROM t"));
        assertThrows(StudioQuackError.class, () -> run(db, Map.of("operation", "query", "query", "SELEC nope")));
        assertEquals(before, countTemp(tmp));
    }

    private static long countTemp(Path dir) throws Exception {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().startsWith("sq-sqlite-")).count();
        }
    }
}
