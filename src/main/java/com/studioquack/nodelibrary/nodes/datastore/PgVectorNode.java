package com.studioquack.nodelibrary.nodes.datastore;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.Json;
import studioquack.StudioQuackHelper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generic PGVector node: QUERY / UPSERT / DELETE against a customer-owned
 * Postgres + pgvector table. No JDBC dependency — simple-query protocol.
 *
 * <p>AI Agent {@code data} port emits {@code operation=QUERY} plus query /
 * embedding / topK / filter. When embedding is empty, QUERY falls back to a
 * substring match on the document column (the agent does not embed).
 */
public final class PgVectorNode implements NodeHandler {

    public static final String DEFAULT_ID = "3f8d2c41-9a70-4e55-b1c8-6d4e0f92a3b5";

    private static final Set<String> OPS = Set.of("QUERY", "UPSERT", "DELETE");

    private final String templateId;

    public PgVectorNode() {
        this(DEFAULT_ID);
    }

    public PgVectorNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        String password = DataSupport.cfgStr(cfg, "password", null);
        int timeout = clampTimeout(DataSupport.cfgInt(cfg, "timeout_seconds", 25));
        try {
            String op = DataSupport.inStrAny(in, DataSupport.cfgStr(cfg, "operation", "QUERY"),
                    "operation", "command", "op").trim().toUpperCase(Locale.ROOT);
            if (!OPS.contains(op)) {
                return DataSupport.invalidInput("unsupported operation");
            }
            String sql = switch (op) {
                case "QUERY" -> buildQuerySql(cfg, in);
                case "UPSERT" -> buildUpsertSql(cfg, in);
                case "DELETE" -> buildDeleteSql(cfg, in);
                default -> throw new IllegalArgumentException("unsupported operation");
            };
            String host = DataSupport.cfgStr(cfg, "host", "127.0.0.1");
            int port = DataSupport.cfgInt(cfg, "port", 5432);
            String user = DataSupport.cfgStr(cfg, "user", "postgres");
            String database = DataSupport.cfgStr(cfg, "database", "postgres");
            boolean ssl = "require".equalsIgnoreCase(DataSupport.cfgStr(cfg, "sslmode", "disable"));
            boolean verify = !"false".equalsIgnoreCase(DataSupport.cfgStr(cfg, "verify_tls", "true"));
            List<Map<String, String>> rows;
            try (PostgresSimpleClient c = PostgresSimpleClient.connect(
                    host, port, user, password, database, timeout, ssl, verify)) {
                rows = c.query(sql);
            }
            List<Object> matches = new ArrayList<>();
            for (Map<String, String> row : rows) {
                matches.add(toMatch(row));
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("matches", matches);
            data.put("value", matches);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("matches", matches);
            body.put("data", data);
            return DataSupport.ports(body);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(DataSupport.redact(
                    e.getMessage() == null || e.getMessage().isBlank() ? "invalid input" : e.getMessage(),
                    password));
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return fail(e.getClass().getSimpleName() + ": " + safeMsg(e), password);
        }
    }

    static String buildQuerySql(Map<String, Object> cfg, Map<String, Object> in) {
        Cols cols = cols(cfg);
        int topK = positive(DataSupport.inInt(in, "topK",
                DataSupport.inInt(in, "limit", DataSupport.cfgInt(cfg, "topK", 5))), 5);
        String embedding = vectorLiteral(firstList(in, "embedding", "vector", "query_embeddings"));
        String where = filterWhere(in.get("filter") != null ? in.get("filter") : in.get("where"), cols);
        StringBuilder sql = new StringBuilder("SELECT ");
        sql.append(cols.id).append(", ").append(cols.document).append(", ").append(cols.metadata);
        if (embedding != null) {
            sql.append(", (1 - (").append(cols.embedding).append(" <=> ").append(embedding)
                    .append("::vector)) AS score");
        }
        sql.append(" FROM ").append(cols.table);
        List<String> clauses = new ArrayList<>();
        if (!where.isEmpty()) {
            clauses.add(where);
        }
        if (embedding == null) {
            String q = DataSupport.inStr(in, "query", "");
            if (q != null && !q.isBlank()) {
                clauses.add("position(" + lit(q) + " in " + cols.document + ") > 0");
            }
        }
        if (!clauses.isEmpty()) {
            sql.append(" WHERE ").append(String.join(" AND ", clauses));
        }
        if (embedding != null) {
            sql.append(" ORDER BY ").append(cols.embedding).append(" <=> ").append(embedding).append("::vector");
        }
        sql.append(" LIMIT ").append(topK);
        return sql.toString();
    }

    static String buildUpsertSql(Map<String, Object> cfg, Map<String, Object> in) {
        Cols cols = cols(cfg);
        String id = DataSupport.inStr(in, "id", null);
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id is required");
        }
        String document = DataSupport.inStr(in, "document", DataSupport.inStr(in, "query", ""));
        String embedding = vectorLiteral(firstList(in, "embedding", "vector"));
        if (embedding == null) {
            throw new IllegalArgumentException("embedding is required");
        }
        Object meta = in.get("metadata") != null ? in.get("metadata") : in.get("filter");
        String metaLit = meta == null ? "'{}'::jsonb" : lit(meta instanceof String s ? s : Json.write(meta)) + "::jsonb";
        return "INSERT INTO " + cols.table + " (" + cols.id + ", " + cols.document + ", "
                + cols.embedding + ", " + cols.metadata + ") VALUES (" + lit(id) + ", " + lit(document)
                + ", " + embedding + "::vector, " + metaLit + ") ON CONFLICT (" + cols.id
                + ") DO UPDATE SET " + cols.document + "=EXCLUDED." + cols.document + ", "
                + cols.embedding + "=EXCLUDED." + cols.embedding + ", "
                + cols.metadata + "=EXCLUDED." + cols.metadata;
    }

    static String buildDeleteSql(Map<String, Object> cfg, Map<String, Object> in) {
        Cols cols = cols(cfg);
        String id = DataSupport.inStr(in, "id", null);
        String where = filterWhere(in.get("filter") != null ? in.get("filter") : in.get("where"), cols);
        if (id != null && !id.isBlank()) {
            where = cols.id + " = " + lit(id) + (where.isEmpty() ? "" : " AND " + where);
        }
        if (where.isEmpty()) {
            throw new IllegalArgumentException("id or filter is required");
        }
        return "DELETE FROM " + cols.table + " WHERE " + where + " RETURNING " + cols.id;
    }

    static String quoteIdent(String raw) {
        if (raw == null || !raw.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            throw new IllegalArgumentException("invalid identifier");
        }
        return "\"" + raw + "\"";
    }

    static String lit(String raw) {
        String s = raw == null ? "" : raw;
        if (s.contains("$sq$")) {
            throw new IllegalArgumentException("value rejected");
        }
        return "$sq$" + s + "$sq$";
    }

    static String vectorLiteral(List<Object> values) {
        if (values == null || values.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder("'[");
        for (int i = 0; i < values.size(); i++) {
            double d;
            try {
                d = Double.parseDouble(String.valueOf(values.get(i)));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("embedding must be numbers");
            }
            if (!Double.isFinite(d)) {
                throw new IllegalArgumentException("embedding must be finite");
            }
            if (i > 0) {
                sb.append(',');
            }
            sb.append(d);
        }
        sb.append("]'");
        return sb.toString();
    }

    private static Cols cols(Map<String, Object> cfg) {
        String schema = DataSupport.cfgStr(cfg, "schema", "public");
        String table = DataSupport.cfgStr(cfg, "table", null);
        if (table == null || table.isBlank()) {
            throw new IllegalArgumentException("table is required");
        }
        return new Cols(
                quoteIdent(schema) + "." + quoteIdent(table),
                quoteIdent(DataSupport.cfgStr(cfg, "id_column", "id")),
                quoteIdent(DataSupport.cfgStr(cfg, "document_column", "document")),
                quoteIdent(DataSupport.cfgStr(cfg, "embedding_column", "embedding")),
                quoteIdent(DataSupport.cfgStr(cfg, "metadata_column", "metadata")));
    }

    private static String filterWhere(Object filter, Cols cols) {
        if (!(filter instanceof Map<?, ?> map) || map.isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            String k = String.valueOf(e.getKey());
            if ("id".equals(k)) {
                parts.add(cols.id + " = " + lit(String.valueOf(e.getValue())));
            } else {
                parts.add(cols.metadata + " ->> " + lit(k) + " = " + lit(String.valueOf(e.getValue())));
            }
        }
        return String.join(" AND ", parts);
    }

    private static List<Object> firstList(Map<String, Object> in, String... keys) {
        if (in == null) {
            return List.of();
        }
        for (String k : keys) {
            Object v = in.get(k);
            if (v instanceof List<?> list) {
                if (!list.isEmpty() && list.get(0) instanceof List<?> inner) {
                    return new ArrayList<>(inner);
                }
                return new ArrayList<>(list);
            }
        }
        return List.of();
    }

    private static Map<String, Object> toMatch(Map<String, String> row) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (row == null) {
            return m;
        }
        if (row.get("id") != null) {
            m.put("id", row.get("id"));
        }
        if (row.get("document") != null) {
            m.put("document", row.get("document"));
        }
        if (row.get("metadata") != null) {
            try {
                m.put("metadata", Json.parse(row.get("metadata")));
            } catch (Exception e) {
                m.put("metadata", row.get("metadata"));
            }
        }
        if (row.get("score") != null) {
            try {
                m.put("score", Double.parseDouble(row.get("score")));
            } catch (NumberFormatException e) {
                m.put("score", row.get("score"));
            }
        }
        return m;
    }

    private static Map<String, Object> fail(String message, String password) {
        return DataSupport.fail(DataSupport.redact(
                message == null || message.isBlank() ? "request failed" : message, password));
    }

    private static String safeMsg(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m;
    }

    private static int clampTimeout(int seconds) {
        if (seconds <= 0) {
            return 25;
        }
        return Math.min(seconds, 60);
    }

    private static int positive(int n, int fallback) {
        return n > 0 ? n : fallback;
    }

    private record Cols(String table, String id, String document, String embedding, String metadata) {
    }
}
