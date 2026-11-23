package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.nodes.datastore.SqlDatabaseNode.Dialect;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import studioquack.StudioQuackError;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * End-to-end run against real servers. Opt-in: {@code -Dsqlnode.live=true}. Each dialect
 * reads {@code sqlnode.<dialect>.host|port|database|user|password|ssl_mode} system
 * properties; the defaults match throwaway local containers on 55432 / 53306 / 51433 / 51521.
 */
@EnabledIfSystemProperty(named = "sqlnode.live", matches = "true")
class SqlDatabaseLiveTest {

    private static Map<String, Object> config(Dialect d) {
        String prefix = "sqlnode." + d.name().toLowerCase() + ".";
        Map<String, Object> defaults = switch (d) {
            case POSTGRESQL -> Map.of("port", "55432", "database", "postgres", "user", "postgres", "ssl_mode", "disable");
            case MYSQL -> Map.of("port", "53306", "database", "shop", "user", "root", "ssl_mode", "require");
            case SQL_SERVER -> Map.of("port", "51433", "database", "master", "user", "sa", "ssl_mode", "require");
            case ORACLE -> Map.of("port", "51521", "database", "FREEPDB1", "user", "sqapp", "ssl_mode", "disable");
            case SQLITE -> throw new IllegalArgumentException("SQLite is covered by SqliteDatabaseNodeTest");
        };
        Map<String, Object> cfg = new LinkedHashMap<>();
        cfg.put("host", System.getProperty(prefix + "host", "127.0.0.1"));
        for (String key : List.of("port", "database", "user", "ssl_mode")) {
            cfg.put(key, System.getProperty(prefix + key, (String) defaults.get(key)));
        }
        cfg.put("password", System.getProperty(prefix + "password",
                d == Dialect.SQL_SERVER ? "Sq_test_Pw1!" : "Sq_test_Pw1"));
        cfg.put("timeout_seconds", "20");
        return cfg;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> run(Dialect d, Map<String, Object> cfg, Map<String, Object> input) {
        Map<String, Object> out = new SqlDatabaseNode(d).handle(SqlDatabaseNodeTest.request(cfg, input), null);
        return (Map<String, Object>) out.get("output");
    }

    private static String createTable(Dialect d) {
        return switch (d) {
            case POSTGRESQL -> "CREATE TABLE sq_live_orders (id INT PRIMARY KEY, customer VARCHAR(50), total NUMERIC(10,2), note TEXT)";
            case MYSQL -> "CREATE TABLE sq_live_orders (id INT PRIMARY KEY, customer VARCHAR(50), total DECIMAL(10,2), note TEXT)";
            case SQL_SERVER -> "CREATE TABLE sq_live_orders (id INT PRIMARY KEY, customer NVARCHAR(50), total DECIMAL(10,2), note NVARCHAR(MAX))";
            case ORACLE -> "CREATE TABLE sq_live_orders (id NUMBER(10) PRIMARY KEY, customer VARCHAR2(50), total NUMBER(10,2), note CLOB)";
            case SQLITE -> throw new IllegalArgumentException("SQLite is covered by SqliteDatabaseNodeTest");
        };
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @EnumSource(value = Dialect.class, names = "SQLITE", mode = EnumSource.Mode.EXCLUDE)
    void queryExecuteInsertRoundTrip(Dialect d) {
        Map<String, Object> cfg = config(d);
        try {
            run(d, cfg, Map.of("operation", "execute", "query", "DROP TABLE sq_live_orders"));
        } catch (StudioQuackError ignored) {
            // table did not exist yet
        }
        run(d, cfg, Map.of("operation", "execute", "query", createTable(d)));

        Map<String, Object> inserted = run(d, cfg, Map.of("operation", "insert", "table", "sq_live_orders",
                "rows", List.of(
                        Map.of("id", 1, "customer", "acme", "total", 12.5, "note", "first"),
                        Map.of("id", 2, "customer", "globex", "total", 99.99))));
        assertEquals(2, ((Number) inserted.get("affected_rows")).intValue());

        Map<String, Object> rows = run(d, cfg, Map.of("operation", "query",
                "query", "SELECT id, customer, total, note FROM sq_live_orders WHERE total > ? ORDER BY id",
                "parameters", List.of(10)));
        List<Map<String, Object>> list = (List<Map<String, Object>>) rows.get("rows");
        assertEquals(2, list.size());
        assertEquals(2, ((Number) rows.get("row_count")).intValue());
        Map<String, Object> first = lowerKeys(list.get(0));
        assertEquals("acme", first.get("customer"));
        assertEquals("first", first.get("note"));
        assertEquals(0, new java.math.BigDecimal("12.50").compareTo(new java.math.BigDecimal(first.get("total").toString())));
        assertEquals(List.of(1L, 2L), list.stream().map(r -> ((Number) lowerKeys(r).get("id")).longValue()).toList());

        Map<String, Object> injected = run(d, cfg, Map.of("operation", "query",
                "query", "SELECT id FROM sq_live_orders WHERE customer = ?", "parameters", List.of("acme' OR '1'='1")));
        assertEquals(0, ((Number) injected.get("row_count")).intValue());

        Map<String, Object> updated = run(d, cfg, Map.of("operation", "execute",
                "query", "UPDATE sq_live_orders SET customer = ? WHERE id = ?", "parameters", List.of("initech", 2)));
        assertEquals(1, ((Number) updated.get("affected_rows")).intValue());

        Map<String, Object> capped = new LinkedHashMap<>(cfg);
        capped.put("max_rows", "1");
        Map<String, Object> truncated = run(d, capped, Map.of("operation", "query",
                "query", "SELECT id FROM sq_live_orders ORDER BY id"));
        assertEquals(1, ((Number) truncated.get("row_count")).intValue());
        assertEquals(Boolean.TRUE, truncated.get("truncated"));

        StudioQuackError duplicate = assertThrows(StudioQuackError.class, () -> run(d, cfg, Map.of(
                "operation", "insert", "table", "sq_live_orders",
                "rows", List.of(Map.of("id", 3, "customer", "x"), Map.of("id", 1, "customer", "dup")))));
        assertEquals("REQUEST_FAILED", duplicate.errorCode());
        Map<String, Object> afterRollback = run(d, cfg, Map.of("operation", "query",
                "query", "SELECT id FROM sq_live_orders WHERE id = ?", "parameters", List.of(3)));
        assertEquals(0, ((Number) afterRollback.get("row_count")).intValue(), "failed batch must roll back");

        run(d, cfg, Map.of("operation", "execute", "query", "DROP TABLE sq_live_orders"));
    }

    @ParameterizedTest
    @EnumSource(value = Dialect.class, names = "SQLITE", mode = EnumSource.Mode.EXCLUDE)
    void wrongPasswordIsAuthFailedAndRedacted(Dialect d) {
        Map<String, Object> cfg = config(d);
        cfg.put("password", "Wrong_Pw_9431");
        StudioQuackError e = assertThrows(StudioQuackError.class,
                () -> run(d, cfg, Map.of("operation", "query", "query", "SELECT 1" + (d == Dialect.ORACLE ? " FROM dual" : ""))));
        assertEquals("AUTH_FAILED", e.errorCode(), e.getMessage());
        assertFalse(e.getMessage().contains("Wrong_Pw_9431"));
        assertTrue(e.data().containsKey("sql_state"));
    }

    private static Map<String, Object> lowerKeys(Map<String, Object> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        row.forEach((k, v) -> out.put(k.toLowerCase(), v));
        return out;
    }
}
