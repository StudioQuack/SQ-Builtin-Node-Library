package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import org.sqlite.SQLiteConfig;
import org.sqlite.SQLiteConnection;
import org.sqlite.SQLiteLimits;
import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.math.BigInteger;
import java.sql.Blob;
import java.sql.Clob;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * One handler for the PostgreSQL, MySQL, Microsoft SQL Server, Oracle Database and
 * SQLite nodes: {@code query} returns rows, {@code execute} runs a write/DDL statement,
 * {@code insert} batch-inserts rows into one table inside a transaction.
 *
 * <p>Values are always bound as {@code ?} parameters, never spliced into SQL. The
 * JDBC URL and driver properties are built here from validated host/port/database
 * fields so a flow cannot inject driver options (local-file reads, class loading).
 *
 * <p>SQLite has no server and the sandbox has no host paths: the database arrives as
 * a file-typed input, is opened from a private temp copy, and {@code execute} /
 * {@code insert} return the updated database as the output {@code file}.
 */
public final class SqlDatabaseNode implements NodeHandler {

    public enum Dialect {
        POSTGRESQL("postgresql", "3fe03f76-b5ed-42e9-8deb-4bcd01cc9fe1", 5432, "postgres", '"', '"'),
        MYSQL("mysql", "884d2d35-27bd-4064-9e7e-f41cc9d8fe5e", 3306, "", '`', '`'),
        SQL_SERVER("microsoft-sql-server", "8010fe7d-bf3a-4e68-be97-37f6041867dc", 1433, "", '[', ']'),
        ORACLE("oracle-database", "43659922-9de6-4814-a515-9c1f55a6b14c", 1521, "", '"', '"'),
        SQLITE("sqlite", "b7c48dc6-836b-4a12-a2e5-2749be0abe80", 0, "", '"', '"');

        final String nodeKey;
        final String defaultId;
        final int defaultPort;
        final String defaultDatabase;
        final char openQuote;
        final char closeQuote;

        Dialect(String nodeKey, String defaultId, int defaultPort, String defaultDatabase,
                char openQuote, char closeQuote) {
            this.nodeKey = nodeKey;
            this.defaultId = defaultId;
            this.defaultPort = defaultPort;
            this.defaultDatabase = defaultDatabase;
            this.openQuote = openQuote;
            this.closeQuote = closeQuote;
        }

        public String nodeKey() {
            return nodeKey;
        }

        public String defaultId() {
            return defaultId;
        }
    }

    private static final Set<String> OPS = Set.of("query", "execute", "insert");
    private static final Set<String> SSL_MODES = Set.of("disable", "require", "verify-full");
    private static final Pattern HOST = Pattern.compile(
            "[A-Za-z0-9](?:[A-Za-z0-9.-]{0,251}[A-Za-z0-9])?|\\[[0-9A-Fa-f:.]{2,45}]");
    private static final Pattern DATABASE = Pattern.compile("[A-Za-z0-9_$#.-]{1,128}");
    private static final Pattern IDENT = Pattern.compile("[A-Za-z_][A-Za-z0-9_$#]{0,127}");
    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int MAX_ROWS_CAP = 10_000;
    private static final int MAX_INSERT_ROWS = 10_000;
    private static final int MAX_LOB_BYTES = 1 << 20;
    private static final int MAX_ERROR_CHARS = 500;

    private final Dialect dialect;
    private final String templateId;

    public SqlDatabaseNode(Dialect dialect) {
        this(dialect, dialect.defaultId);
    }

    public SqlDatabaseNode(Dialect dialect, String templateId) {
        this.dialect = dialect;
        this.templateId = (templateId == null || templateId.isBlank()) ? dialect.defaultId : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.overlayConfig(DataSupport.readInput(request, helper), cfg,
                "operation", "query", "parameters", "table", "rows", "database");
        String password = DataSupport.cfgStr(cfg, "password", null);
        SqliteFile sqlite = null;
        try {
            String op = DataSupport.inStr(in, "operation", "query").toLowerCase(Locale.ROOT);
            if (!OPS.contains(op)) {
                return DataSupport.invalidInput("unsupported operation: use query, execute or insert");
            }
            int timeout = clamp(DataSupport.cfgInt(cfg, "timeout_seconds", 30), 1, 300);
            int maxRows = clamp(DataSupport.cfgInt(cfg, "max_rows", DEFAULT_MAX_ROWS), 1, MAX_ROWS_CAP);
            Connect target = dialect == Dialect.SQLITE ? null : connectTarget(dialect, cfg, timeout);
            Plan plan = "insert".equals(op) ? insertPlan(dialect, in) : statementPlan(in);
            boolean writes = !"query".equals(op);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("operation", op);
            if (dialect == Dialect.SQLITE) {
                sqlite = SqliteFile.materialise(in.get("database"));
            }
            try (Connection conn = sqlite != null
                    ? sqlite.open(!writes, timeout) : open(dialect, target, password)) {
                if ("insert".equals(op)) {
                    runInsert(conn, plan, timeout, body);
                } else {
                    runStatement(conn, op, plan, timeout, maxRows, body);
                }
            }
            if (sqlite != null && writes) {
                body.put("file", sqlite.toFileBlock());
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("value", body.containsKey("rows") ? body.get("rows") : body.get("affected_rows"));
            body.put("data", data);
            return DataSupport.ports(body);
        } catch (StudioQuackError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(DataSupport.redact(e.getMessage(), password));
        } catch (SQLException e) {
            throw sqlFailure(dialect, e, password);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw DataSupport.failError(shorten(DataSupport.redact(
                    e.getClass().getSimpleName() + ": " + e.getMessage(), password)));
        } finally {
            if (sqlite != null) {
                sqlite.delete();
            }
        }
    }

    // ---- connection ---------------------------------------------------------

    record Connect(String url, Properties props) {
    }

    static Connect connectTarget(Dialect d, Map<String, Object> cfg, int timeoutSeconds) {
        if (d == Dialect.SQLITE) {
            throw new IllegalArgumentException("SQLite has no network connection");
        }
        String host = DataSupport.cfgStr(cfg, "host", "");
        if (!HOST.matcher(host).matches()) {
            throw new IllegalArgumentException("host must be a hostname or IP address (no scheme, port or path)");
        }
        int port = DataSupport.cfgInt(cfg, "port", d.defaultPort);
        if (port > 65535) {
            throw new IllegalArgumentException("port must be between 1 and 65535");
        }
        String database = DataSupport.cfgStr(cfg, "database", d.defaultDatabase);
        if (!database.isEmpty() && !DATABASE.matcher(database).matches()) {
            throw new IllegalArgumentException("database may contain only letters, digits, _ $ # . -");
        }
        if (database.isEmpty() && (d == Dialect.ORACLE || d == Dialect.POSTGRESQL)) {
            throw new IllegalArgumentException(d == Dialect.ORACLE
                    ? "database (service name) is required" : "database is required");
        }
        String ssl = DataSupport.cfgStr(cfg, "ssl_mode", "require").toLowerCase(Locale.ROOT);
        if (!SSL_MODES.contains(ssl)) {
            throw new IllegalArgumentException("ssl_mode must be disable, require or verify-full");
        }
        String user = DataSupport.cfgStr(cfg, "user", "");
        if (user.isEmpty()) {
            throw new IllegalArgumentException("user is required");
        }
        String timeoutMs = String.valueOf(timeoutSeconds * 1000L);
        String timeoutS = String.valueOf(timeoutSeconds);
        Properties p = new Properties();
        p.setProperty("user", user);
        String url;
        switch (d) {
            case POSTGRESQL -> {
                url = "jdbc:postgresql://" + host + ":" + port + "/" + database;
                p.setProperty("sslmode", ssl);
                if ("verify-full".equals(ssl)) {
                    p.setProperty("sslfactory", "org.postgresql.ssl.DefaultJavaSSLFactory");
                }
                p.setProperty("connectTimeout", timeoutS);
                p.setProperty("loginTimeout", timeoutS);
                p.setProperty("socketTimeout", timeoutS);
                p.setProperty("ApplicationName", "StudioQuack");
            }
            case MYSQL -> {
                url = "jdbc:mariadb://" + host + ":" + port + "/" + database;
                p.setProperty("sslMode", switch (ssl) {
                    case "disable" -> "disable";
                    case "require" -> "trust";
                    default -> "verify-full";
                });
                p.setProperty("connectTimeout", timeoutMs);
                p.setProperty("socketTimeout", timeoutMs);
                p.setProperty("allowLocalInfile", "false");
                p.setProperty("allowPublicKeyRetrieval", String.valueOf(
                        "true".equalsIgnoreCase(DataSupport.cfgStr(cfg, "allow_public_key_retrieval", "false"))));
            }
            case SQL_SERVER -> {
                url = "jdbc:sqlserver://" + host + ":" + port;
                if (!database.isEmpty()) {
                    p.setProperty("databaseName", database);
                }
                p.setProperty("encrypt", "disable".equals(ssl) ? "false" : "true");
                p.setProperty("trustServerCertificate", String.valueOf("require".equals(ssl)));
                p.setProperty("loginTimeout", timeoutS);
                p.setProperty("socketTimeout", timeoutMs);
                p.setProperty("applicationName", "StudioQuack");
            }
            default -> {
                String protocol = "disable".equals(ssl) ? "" : "tcps://";
                url = "jdbc:oracle:thin:@" + (protocol.isEmpty() ? "//" : protocol)
                        + host + ":" + port + "/" + database;
                if (!protocol.isEmpty()) {
                    p.setProperty("oracle.net.ssl_server_dn_match", String.valueOf("verify-full".equals(ssl)));
                }
                p.setProperty("oracle.net.CONNECT_TIMEOUT", timeoutMs);
                p.setProperty("oracle.jdbc.ReadTimeout", timeoutMs);
            }
        }
        return new Connect(url, p);
    }

    private static Connection open(Dialect d, Connect target, String password) throws SQLException {
        Properties props = new Properties();
        props.putAll(target.props());
        if (password != null) {
            props.setProperty("password", password);
        }
        Driver driver = switch (d) {
            case POSTGRESQL -> new org.postgresql.Driver();
            case MYSQL -> new org.mariadb.jdbc.Driver();
            case SQL_SERVER -> new com.microsoft.sqlserver.jdbc.SQLServerDriver();
            case ORACLE -> ExternalJdbcDriver.load("oracle.jdbc.OracleDriver", "Oracle");
            case SQLITE -> throw new IllegalStateException("SQLite opens through SqliteFile");
        };
        Connection conn = driver.connect(target.url(), props);
        if (conn == null) {
            throw new SQLException("driver rejected the connection URL", "08001");
        }
        return conn;
    }

    /** Private temp copy of a SQLite database, deleted with its journal files after the call. */
    static final class SqliteFile {

        static final String MIME = "application/vnd.sqlite3";
        private static final String DEFAULT_NAME = "database.sqlite";

        private final Path path;
        private final String name;

        private SqliteFile(Path path, String name) {
            this.path = path;
            this.name = name == null || name.isBlank() || "file".equals(name) ? DEFAULT_NAME : name;
        }

        /** Blank input starts a new, empty database; otherwise the file value is copied (8 MiB cap). */
        static SqliteFile materialise(Object raw) throws IOException {
            Path path = Files.createTempFile("sq-sqlite-", ".db");
            if (raw == null || (raw instanceof String s && s.isBlank())
                    || (raw instanceof Map<?, ?> m && m.isEmpty()) || (raw instanceof List<?> l && l.isEmpty())) {
                return new SqliteFile(path, DEFAULT_NAME);
            }
            try {
                FilesSupport.FileBytes file = FilesSupport.readFileValue(raw, "database");
                Files.write(path, file.bytes());
                return new SqliteFile(path, file.name());
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(path);
                throw e;
            }
        }

        /**
         * ATTACH is disabled so SQL cannot open other files in the container, and the page
         * count is capped so the database cannot outgrow the node's file limit. Queries run
         * with {@code query_only} rather than a read-only open, which WAL files can refuse.
         */
        Connection open(boolean queryOnly, int timeoutSeconds) throws SQLException {
            SQLiteConfig config = new SQLiteConfig();
            config.enableLoadExtension(false);
            config.setBusyTimeout(timeoutSeconds * 1000);
            Connection conn = new org.sqlite.JDBC().connect("jdbc:sqlite:" + path, config.toProperties());
            try {
                conn.unwrap(SQLiteConnection.class).setLimit(SQLiteLimits.SQLITE_LIMIT_ATTACHED, 0);
                try (java.sql.Statement st = conn.createStatement()) {
                    int pageSize;
                    try (ResultSet rs = st.executeQuery("PRAGMA page_size")) {
                        pageSize = rs.next() ? Math.max(rs.getInt(1), 512) : 4096;
                    }
                    st.execute("PRAGMA max_page_count = " + FilesSupport.MAX_FILE_BYTES / pageSize);
                    if (queryOnly) {
                        st.execute("PRAGMA query_only = ON");
                    }
                }
                return conn;
            } catch (SQLException | RuntimeException e) {
                conn.close();
                throw e;
            }
        }

        Map<String, Object> toFileBlock() throws IOException {
            return FilesSupport.fileBlock(name, MIME, Files.readAllBytes(path));
        }

        void delete() {
            for (String suffix : List.of("", "-journal", "-wal", "-shm")) {
                try {
                    Files.deleteIfExists(Path.of(path + suffix));
                } catch (IOException ignored) {
                    // tmpfs is reclaimed with the container
                }
            }
        }
    }

    // ---- operations ---------------------------------------------------------

    /** SQL text plus one parameter list per execution (one for query/execute, one per row for insert). */
    record Plan(String sql, List<String> columns, List<List<Object>> batches) {
    }

    static Plan statementPlan(Map<String, Object> in) {
        String sql = DataSupport.inStrAny(in, "", "query", "sql");
        if (sql.isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        Object raw = in.get("parameters");
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            raw = in.get("params");
        }
        return new Plan(sql, List.of(), List.of(listValue(raw, "parameters")));
    }

    static Plan insertPlan(Dialect d, Map<String, Object> in) {
        String table = DataSupport.inStr(in, "table", "");
        if (table.isEmpty()) {
            throw new IllegalArgumentException("table is required for insert");
        }
        List<Object> raw = listValue(in.get("rows"), "rows");
        if (raw.isEmpty()) {
            throw new IllegalArgumentException("rows must be a non-empty list of objects");
        }
        if (raw.size() > MAX_INSERT_ROWS) {
            throw new IllegalArgumentException("rows exceeds " + MAX_INSERT_ROWS + " per call");
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> columnSet = new LinkedHashSet<>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("each row must be a JSON object of column → value");
            }
            Map<String, Object> row = Json.asMap(item);
            rows.add(row);
            columnSet.addAll(row.keySet());
        }
        List<String> columns = new ArrayList<>(columnSet);
        List<List<Object>> batches = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            List<Object> values = new ArrayList<>();
            for (String col : columns) {
                values.add(row.get(col));
            }
            batches.add(values);
        }
        return new Plan(buildInsertSql(d, table, columns), columns, batches);
    }

    private static void runStatement(Connection conn, String op, Plan plan, int timeout,
                                     int maxRows, Map<String, Object> body) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(plan.sql())) {
            ps.setQueryTimeout(timeout);
            ps.setMaxRows(maxRows + 1);
            ps.setFetchSize(Math.min(maxRows + 1, 500));
            bind(ps, plan.batches().get(0));
            if ("query".equals(op)) {
                try (ResultSet rs = ps.executeQuery()) {
                    readRows(rs, maxRows, body);
                }
                return;
            }
            if (ps.execute()) {
                try (ResultSet rs = ps.getResultSet()) {
                    readRows(rs, maxRows, body);
                }
            }
            body.put("affected_rows", Math.max(ps.getUpdateCount(), 0));
        }
    }

    private static void runInsert(Connection conn, Plan plan, int timeout,
                                  Map<String, Object> body) throws SQLException {
        boolean auto = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try (PreparedStatement ps = conn.prepareStatement(plan.sql())) {
            ps.setQueryTimeout(timeout);
            for (List<Object> values : plan.batches()) {
                bind(ps, values);
                ps.addBatch();
            }
            int affected = 0;
            for (int n : ps.executeBatch()) {
                affected += n == java.sql.Statement.SUCCESS_NO_INFO ? 1 : Math.max(n, 0);
            }
            conn.commit();
            body.put("affected_rows", affected);
            body.put("columns", plan.columns());
        } catch (SQLException | RuntimeException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(auto);
        }
    }

    static String buildInsertSql(Dialect d, String table, List<String> columns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(qualified(d, table)).append(" (");
        StringBuilder marks = new StringBuilder();
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(", ");
                marks.append(", ");
            }
            sb.append(quoteIdent(d, columns.get(i)));
            marks.append('?');
        }
        return sb.append(") VALUES (").append(marks).append(')').toString();
    }

    static String qualified(Dialect d, String name) {
        String[] parts = name.trim().split("\\.", -1);
        if (parts.length > 3) {
            throw new IllegalArgumentException("table must be table, schema.table or database.schema.table");
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(quoteIdent(d, parts[i]));
        }
        return sb.toString();
    }

    /**
     * Quote one identifier. The allowlist excludes every quote character, so the
     * quoted form cannot break out. Oracle names are upper-cased first because
     * Oracle stores unquoted identifiers in upper case.
     */
    static String quoteIdent(Dialect d, String raw) {
        String name = raw == null ? "" : raw.trim();
        if (!IDENT.matcher(name).matches()) {
            throw new IllegalArgumentException("invalid identifier: " + shorten(name));
        }
        if (d == Dialect.ORACLE) {
            name = name.toUpperCase(Locale.ROOT);
        }
        return d.openQuote + name + d.closeQuote;
    }

    // ---- values -------------------------------------------------------------

    static List<Object> listValue(Object raw, String field) {
        if (raw == null) {
            return List.of();
        }
        if (raw instanceof List<?> list) {
            return new ArrayList<>(list);
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return List.of();
            }
            Object parsed;
            try {
                parsed = Json.parse(t);
            } catch (RuntimeException e) {
                throw new IllegalArgumentException(field + " must be a JSON array");
            }
            if (parsed instanceof List<?> list) {
                return new ArrayList<>(list);
            }
        }
        throw new IllegalArgumentException(field + " must be a JSON array");
    }

    private static void bind(PreparedStatement ps, List<Object> values) throws SQLException {
        for (int i = 0; i < values.size(); i++) {
            Object v = values.get(i);
            if (v instanceof Map<?, ?> || v instanceof List<?>) {
                ps.setObject(i + 1, Json.write(v));
            } else {
                ps.setObject(i + 1, v);
            }
        }
    }

    private static void readRows(ResultSet rs, int maxRows, Map<String, Object> body) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        int n = md.getColumnCount();
        List<String> columns = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            columns.add(md.getColumnLabel(i));
        }
        List<Object> rows = new ArrayList<>();
        boolean truncated = false;
        while (rs.next()) {
            if (rows.size() == maxRows) {
                truncated = true;
                break;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= n; i++) {
                row.put(columns.get(i - 1), jsonValue(rs, i));
            }
            rows.add(row);
        }
        body.put("rows", rows);
        body.put("columns", columns);
        body.put("row_count", rows.size());
        body.put("truncated", truncated);
    }

    static Object jsonValue(ResultSet rs, int i) throws SQLException {
        Object v = rs.getObject(i);
        if (v == null) {
            return null;
        }
        if (v instanceof String || v instanceof Boolean || v instanceof Integer || v instanceof Long
                || v instanceof Short || v instanceof Byte || v instanceof BigInteger) {
            return v;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        if (v instanceof Double || v instanceof Float) {
            double x = ((Number) v).doubleValue();
            return Double.isFinite(x) ? v : String.valueOf(x);
        }
        if (v instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes.length <= MAX_LOB_BYTES
                    ? bytes : Arrays.copyOf(bytes, MAX_LOB_BYTES));
        }
        if (v instanceof Blob blob) {
            return Base64.getEncoder().encodeToString(
                    blob.getBytes(1, (int) Math.min(blob.length(), MAX_LOB_BYTES)));
        }
        if (v instanceof Clob clob) {
            return clob.getSubString(1, (int) Math.min(clob.length(), MAX_LOB_BYTES));
        }
        return rs.getString(i);
    }

    // ---- errors -------------------------------------------------------------

    static StudioQuackError sqlFailure(Dialect d, SQLException e, String password) {
        String state = e.getSQLState() == null ? "" : e.getSQLState();
        int code = e.getErrorCode();
        if (d == Dialect.SQLITE) {
            int primary = code & 0xff;
            if (primary == 26 || primary == 11) {
                return new StudioQuackError("INVALID_INPUT", "database is not a valid SQLite file",
                        Map.of("sql_state", state, "vendor_code", code));
            }
            if (primary == 13) {
                return new StudioQuackError("REQUEST_FAILED", "SQLite database reached the "
                        + FilesSupport.MAX_FILE_BYTES + " byte node file limit",
                        Map.of("sql_state", state, "vendor_code", code));
            }
        }
        boolean auth = state.startsWith("28")
                || (d == Dialect.SQL_SERVER && code == 18456)
                || (d == Dialect.ORACLE && code == 1017)
                || (d == Dialect.MYSQL && code == 1045);
        String message = shorten(DataSupport.redact("SQL error"
                + (state.isEmpty() ? "" : " (SQLState " + state + ")") + ": " + e.getMessage(), password));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("sql_state", state);
        data.put("vendor_code", code);
        return new StudioQuackError(auth ? "AUTH_FAILED" : "REQUEST_FAILED", message, data);
    }

    private static String shorten(String s) {
        String t = s == null ? "" : s;
        return t.length() <= MAX_ERROR_CHARS ? t : t.substring(0, MAX_ERROR_CHARS);
    }

    private static int clamp(int n, int lo, int hi) {
        return Math.max(lo, Math.min(hi, n));
    }
}
