package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.nodes.datastore.SqlDatabaseNode.Dialect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studioquack.StudioQuackError;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** URL/property building, identifier quoting and input planning for the SQL database nodes. */
class SqlDatabaseNodeTest {

    private static Map<String, Object> cfg(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("host", "db.example.com");
        m.put("user", "app");
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    static Map<String, Object> request(Map<String, Object> config, Object input) {
        List<Object> params = new ArrayList<>();
        config.forEach((k, v) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", k);
            row.put("value", v);
            params.add(row);
        });
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        if (input != null) {
            request.put("inputVariables", Map.of("input", input));
        }
        return request;
    }

    @Test
    void postgresUrlAndTls() {
        var t = SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, cfg("database", "app"), 30);
        assertEquals("jdbc:postgresql://db.example.com:5432/app", t.url());
        assertEquals("require", t.props().getProperty("sslmode"));
        assertNull(t.props().getProperty("sslfactory"));
        assertEquals("30", t.props().getProperty("socketTimeout"));
        assertNull(t.props().getProperty("password"));

        var verify = SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, cfg("ssl_mode", "verify-full"), 30);
        assertEquals("jdbc:postgresql://db.example.com:5432/postgres", verify.url());
        assertEquals("org.postgresql.ssl.DefaultJavaSSLFactory", verify.props().getProperty("sslfactory"));
    }

    @Test
    void mysqlUsesMariaDbDriverWithLocalInfileOff() {
        var t = SqlDatabaseNode.connectTarget(Dialect.MYSQL, cfg("database", "shop", "port", "3307"), 10);
        assertEquals("jdbc:mariadb://db.example.com:3307/shop", t.url());
        assertEquals("trust", t.props().getProperty("sslMode"));
        assertEquals("false", t.props().getProperty("allowLocalInfile"));
        assertEquals("false", t.props().getProperty("allowPublicKeyRetrieval"));
        assertEquals("10000", t.props().getProperty("connectTimeout"));

        var keyRetrieval = SqlDatabaseNode.connectTarget(Dialect.MYSQL,
                cfg("ssl_mode", "disable", "allow_public_key_retrieval", "true"), 10);
        assertEquals("jdbc:mariadb://db.example.com:3306/", keyRetrieval.url());
        assertEquals("disable", keyRetrieval.props().getProperty("sslMode"));
        assertEquals("true", keyRetrieval.props().getProperty("allowPublicKeyRetrieval"));
    }

    @Test
    void sqlServerPassesDatabaseAsPropertyNotUrl() {
        var t = SqlDatabaseNode.connectTarget(Dialect.SQL_SERVER, cfg("database", "Sales"), 30);
        assertEquals("jdbc:sqlserver://db.example.com:1433", t.url());
        assertEquals("Sales", t.props().getProperty("databaseName"));
        assertEquals("true", t.props().getProperty("encrypt"));
        assertEquals("true", t.props().getProperty("trustServerCertificate"));

        var verify = SqlDatabaseNode.connectTarget(Dialect.SQL_SERVER, cfg("ssl_mode", "verify-full"), 30);
        assertEquals("false", verify.props().getProperty("trustServerCertificate"));
        assertNull(verify.props().getProperty("databaseName"));
        var plain = SqlDatabaseNode.connectTarget(Dialect.SQL_SERVER, cfg("ssl_mode", "disable"), 30);
        assertEquals("false", plain.props().getProperty("encrypt"));
    }

    @Test
    void oracleUsesServiceNameAndTcpsForTls() {
        var plain = SqlDatabaseNode.connectTarget(Dialect.ORACLE,
                cfg("database", "FREEPDB1", "ssl_mode", "disable"), 30);
        assertEquals("jdbc:oracle:thin:@//db.example.com:1521/FREEPDB1", plain.url());
        assertNull(plain.props().getProperty("oracle.net.ssl_server_dn_match"));

        var verify = SqlDatabaseNode.connectTarget(Dialect.ORACLE,
                cfg("database", "orclpdb.example.com", "ssl_mode", "verify-full", "port", "2484"), 30);
        assertEquals("jdbc:oracle:thin:@tcps://db.example.com:2484/orclpdb.example.com", verify.url());
        assertEquals("true", verify.props().getProperty("oracle.net.ssl_server_dn_match"));

        var err = assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.connectTarget(Dialect.ORACLE, cfg(), 30));
        assertTrue(err.getMessage().contains("service name"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"db.example.com/other", "db.example.com?socketFactory=x", "db.example.com:5432",
            "jdbc:postgresql://evil", "db example", "-db", "db;encrypt=false", ""})
    void hostCannotCarryUrlOrDriverOptions(String host) {
        for (Dialect d : Dialect.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> SqlDatabaseNode.connectTarget(d, cfg("host", host, "database", "app"), 30), d + " " + host);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"app?sslfactory=x", "app;encrypt=false", "app/x", "app&allowLoadLocalInfile=true",
            "app user"})
    void databaseCannotCarryDriverOptions(String database) {
        for (Dialect d : Dialect.values()) {
            assertThrows(IllegalArgumentException.class,
                    () -> SqlDatabaseNode.connectTarget(d, cfg("database", database), 30), d + " " + database);
        }
    }

    @Test
    void acceptsIpAddresses() {
        assertEquals("jdbc:postgresql://10.0.0.5:5432/postgres",
                SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, cfg("host", "10.0.0.5"), 5).url());
        assertEquals("jdbc:mariadb://[::1]:3306/",
                SqlDatabaseNode.connectTarget(Dialect.MYSQL, cfg("host", "[::1]"), 5).url());
    }

    @Test
    void rejectsBadSslModePortAndMissingUser() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, cfg("ssl_mode", "allow"), 5));
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, cfg("port", "70000"), 5));
        Map<String, Object> noUser = cfg();
        noUser.remove("user");
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.connectTarget(Dialect.POSTGRESQL, noUser, 5));
    }

    @Test
    void identifiersAreQuotedPerDialect() {
        assertEquals("\"sales\".\"orders\"", SqlDatabaseNode.qualified(Dialect.POSTGRESQL, "sales.orders"));
        assertEquals("`orders`", SqlDatabaseNode.qualified(Dialect.MYSQL, "orders"));
        assertEquals("[db].[dbo].[orders]", SqlDatabaseNode.qualified(Dialect.SQL_SERVER, "db.dbo.orders"));
        assertEquals("\"APP\".\"ORDERS\"", SqlDatabaseNode.qualified(Dialect.ORACLE, "app.orders"));
        assertThrows(IllegalArgumentException.class, () -> SqlDatabaseNode.qualified(Dialect.POSTGRESQL, "a.b.c.d"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"x\"; DROP TABLE t; --", "a`b", "a]b", "a b", "1abc", "", "orders;"})
    void identifierInjectionIsRejected(String name) {
        for (Dialect d : Dialect.values()) {
            assertThrows(IllegalArgumentException.class, () -> SqlDatabaseNode.quoteIdent(d, name), d + " " + name);
        }
    }

    @Test
    void insertPlanUnionsColumnsAndBindsMissingAsNull() {
        var plan = SqlDatabaseNode.insertPlan(Dialect.POSTGRESQL, Map.of(
                "table", "orders",
                "rows", "[{\"id\":1,\"total\":9.5},{\"id\":2,\"note\":\"x\"}]"));
        assertEquals("INSERT INTO \"orders\" (\"id\", \"total\", \"note\") VALUES (?, ?, ?)", plan.sql());
        assertEquals(List.of("id", "total", "note"), plan.columns());
        assertEquals(2, plan.batches().size());
        assertEquals(Arrays.asList(2L, null, "x"), normalize(plan.batches().get(1)));
    }

    @Test
    void insertPlanRejectsBadRows() {
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.insertPlan(Dialect.MYSQL, Map.of("table", "t", "rows", List.of())));
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.insertPlan(Dialect.MYSQL, Map.of("table", "t", "rows", List.of(1, 2))));
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.insertPlan(Dialect.MYSQL, Map.of("rows", List.of(Map.of("a", 1)))));
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.insertPlan(Dialect.MYSQL,
                        Map.of("table", "t", "rows", List.of(Map.of("bad col", 1)))));
    }

    @Test
    void statementPlanReadsParametersFromJsonTextOrAlias() {
        var fromText = SqlDatabaseNode.statementPlan(Map.of("query", "SELECT ?", "parameters", "[42, \"a\"]"));
        assertEquals(List.of(42L, "a"), normalize(fromText.batches().get(0)));
        var alias = SqlDatabaseNode.statementPlan(Map.of("sql", "SELECT ?", "parameters", "", "params", List.of(1)));
        assertEquals("SELECT ?", alias.sql());
        assertEquals(1, alias.batches().get(0).size());
        assertThrows(IllegalArgumentException.class, () -> SqlDatabaseNode.statementPlan(Map.of("query", " ")));
        assertThrows(IllegalArgumentException.class,
                () -> SqlDatabaseNode.statementPlan(Map.of("query", "SELECT 1", "parameters", "{\"a\":1}")));
    }

    @Test
    void invalidRequestsFailBeforeConnecting() {
        SqlDatabaseNode node = new SqlDatabaseNode(Dialect.POSTGRESQL);
        Map<String, Object> config = cfg("host", "127.0.0.1", "port", "1", "password", "s3cret-pw");
        StudioQuackError badOp = assertThrows(StudioQuackError.class,
                () -> node.handle(request(config, Map.of("operation", "drop")), null));
        assertEquals("INVALID_INPUT", badOp.errorCode());
        StudioQuackError noSql = assertThrows(StudioQuackError.class,
                () -> node.handle(request(config, Map.of("operation", "query")), null));
        assertEquals("INVALID_INPUT", noSql.errorCode());
        assertTrue(noSql.getMessage().contains("query is required"));
    }

    @Test
    void sqlErrorsMapAuthAndRedactPassword() {
        StudioQuackError auth = SqlDatabaseNode.sqlFailure(Dialect.ORACLE,
                new SQLException("ORA-01017: invalid credential; pw s3cret-pw", "72000", 1017), "s3cret-pw");
        assertEquals("AUTH_FAILED", auth.errorCode());
        assertFalse(auth.getMessage().contains("s3cret-pw"));
        assertEquals(1017, auth.data().get("vendor_code"));

        assertEquals("AUTH_FAILED", SqlDatabaseNode.sqlFailure(Dialect.POSTGRESQL,
                new SQLException("password authentication failed", "28P01", 0), null).errorCode());
        assertEquals("AUTH_FAILED", SqlDatabaseNode.sqlFailure(Dialect.SQL_SERVER,
                new SQLException("Login failed", "S0001", 18456), null).errorCode());
        StudioQuackError syntax = SqlDatabaseNode.sqlFailure(Dialect.MYSQL,
                new SQLException("You have an error in your SQL syntax", "42000", 1064), null);
        assertEquals("REQUEST_FAILED", syntax.errorCode());
        assertEquals("42000", syntax.data().get("sql_state"));
    }

    private static List<Object> normalize(List<Object> values) {
        List<Object> out = new ArrayList<>();
        for (Object v : values) {
            out.add(v instanceof Number n && !(v instanceof Double) ? (Object) n.longValue() : v);
        }
        return out;
    }
}
