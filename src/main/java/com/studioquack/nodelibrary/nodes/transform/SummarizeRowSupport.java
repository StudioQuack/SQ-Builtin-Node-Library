package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Parses {@code items} rows (maps, scalars, or encoded strings) into per-field value lists. */
final class SummarizeRowSupport {

    static final class CsvState {
        boolean headerConsumed;
    }

    private SummarizeRowSupport() {
    }

    static List<Object> valuesForField(Object item, String field, List<String> fields, CsvState csv) {
        List<Object> out = new ArrayList<>();
        collect(item, field, fields, csv, out);
        return out;
    }

    private static void collect(Object item, String field, List<String> fields, CsvState csv,
                                List<Object> out) {
        if (item == null) {
            return;
        }
        if (item instanceof Map<?, ?> map) {
            Object v = TransformSupport.getPath(EmulatorUtil.mutableMap(map), field);
            if (v != null) {
                out.add(v);
            }
            return;
        }
        if (item instanceof List<?> list) {
            Map<String, Object> row = positionalRow(list, fields);
            Object v = row.get(field);
            if (v != null) {
                out.add(v);
            }
            return;
        }
        if (item instanceof Number) {
            if (fields.size() == 1 && field.equals(fields.get(0))) {
                out.add(item);
            }
            return;
        }
        if (!(item instanceof String s)) {
            return;
        }
        String t = s.trim();
        if (t.isEmpty()) {
            return;
        }

        if (t.startsWith("{")) {
            Object parsed = tryParseJson(t);
            if (parsed instanceof Map<?, ?> m) {
                Object v = TransformSupport.getPath(EmulatorUtil.mutableMap(m), field);
                if (v != null) {
                    out.add(v);
                }
            }
            return;
        }
        if (t.startsWith("[")) {
            Object parsed = tryParseJson(t);
            if (parsed instanceof List<?> list) {
                Map<String, Object> row = positionalRow(list, fields);
                Object v = row.get(field);
                if (v != null) {
                    out.add(v);
                }
            }
            return;
        }

        if (t.contains("\n")) {
            String[] lines = t.split("\\r?\\n");
            List<String> nonEmpty = new ArrayList<>();
            for (String line : lines) {
                String ln = line.trim();
                if (!ln.isEmpty()) {
                    nonEmpty.add(ln);
                }
            }
            if (nonEmpty.size() > 1 && fields.size() == 1 && field.equals(fields.get(0))) {
                if (nonEmpty.get(0).equals(field)) {
                    for (int i = 1; i < nonEmpty.size(); i++) {
                        out.add(coerceCell(nonEmpty.get(i)));
                    }
                    return;
                }
                if (allNumeric(nonEmpty)) {
                    for (String ln : nonEmpty) {
                        out.add(coerceCell(ln));
                    }
                    return;
                }
            }
        }

        if (t.contains("\t")) {
            String[] parts = t.split("\t");
            if (parts.length == fields.size()) {
                addFromColumns(parts, field, fields, out);
                return;
            }
            if (fields.size() == 1 && field.equals(fields.get(0))) {
                for (String part : parts) {
                    String p = part.trim();
                    if (!p.isEmpty()) {
                        out.add(coerceCell(p));
                    }
                }
                return;
            }
        }

        if (t.contains(",")) {
            String[] parts = splitCsvRow(t);
            if (parts.length == fields.size()) {
                if (headerRow(parts, fields) && !csv.headerConsumed) {
                    csv.headerConsumed = true;
                    return;
                }
                addFromColumns(parts, field, fields, out);
                return;
            }
        }

        Map<String, Object> kv = parseDelimitedKeyValue(t);
        if (!kv.isEmpty()) {
            if (kv.containsKey(field)) {
                out.add(kv.get(field));
            }
            return;
        }

        if (fields.size() == 1 && field.equals(fields.get(0))) {
            out.add(coerceCell(t));
        }
    }

    private static void addFromColumns(String[] parts, String field, List<String> fields,
                                       List<Object> out) {
        for (int i = 0; i < fields.size() && i < parts.length; i++) {
            if (fields.get(i).equals(field)) {
                String cell = parts[i].trim();
                if (!cell.isEmpty()) {
                    out.add(coerceCell(cell));
                }
            }
        }
    }

    private static boolean headerRow(String[] parts, List<String> fields) {
        for (int i = 0; i < fields.size(); i++) {
            if (!parts[i].trim().equals(fields.get(i))) {
                return false;
            }
        }
        return true;
    }

    private static Map<String, Object> positionalRow(List<?> list, List<String> fields) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < fields.size() && i < list.size(); i++) {
            row.put(fields.get(i), list.get(i));
        }
        return row;
    }

    private static Map<String, Object> parseDelimitedKeyValue(String t) {
        Map<String, Object> row = new LinkedHashMap<>();
        int eq = t.indexOf('=');
        int colon = t.indexOf(':');
        if (eq > 0 && (colon < 0 || eq < colon)) {
            row.put(t.substring(0, eq).trim(), coerceCell(t.substring(eq + 1).trim()));
            return row;
        }
        if (colon > 0) {
            row.put(t.substring(0, colon).trim(), coerceCell(t.substring(colon + 1).trim()));
            return row;
        }
        int sp = t.indexOf(' ');
        if (sp > 0 && fieldsLookLikeName(t.substring(0, sp))) {
            row.put(t.substring(0, sp).trim(), coerceCell(t.substring(sp + 1).trim()));
        }
        return row;
    }

    private static boolean fieldsLookLikeName(String head) {
        return head.chars().allMatch(ch -> Character.isLetter(ch) || ch == '_' || ch == '$');
    }

    private static String[] splitCsvRow(String t) {
        List<String> parts = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuote = false;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '"') {
                inQuote = !inQuote;
                continue;
            }
            if (c == ',' && !inQuote) {
                parts.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(c);
            }
        }
        parts.add(cur.toString());
        return parts.toArray(String[]::new);
    }

    private static Object coerceCell(String cell) {
        if (cell.isEmpty()) {
            return "";
        }
        if (cell.startsWith("\"") && cell.endsWith("\"") && cell.length() >= 2) {
            cell = cell.substring(1, cell.length() - 1);
        }
        Double d = TransformSupport.toDouble(cell);
        if (d != null) {
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                return d.longValue();
            }
            return d;
        }
        return cell;
    }

    private static boolean allNumeric(List<String> lines) {
        for (String ln : lines) {
            if (TransformSupport.toDouble(ln) == null) {
                return false;
            }
        }
        return true;
    }

    private static Object tryParseJson(String t) {
        try {
            return Json.parse(t);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception ignored) {
            return null;
        }
    }
}
