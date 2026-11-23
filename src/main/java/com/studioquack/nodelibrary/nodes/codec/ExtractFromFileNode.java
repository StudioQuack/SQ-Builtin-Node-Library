package com.studioquack.nodelibrary.nodes.codec;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import studioquack.StudioQuackHelper;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structured extraction from a platform file ref:
 * {@code from_csv | from_xlsx | from_xls | from_ods | from_json | from_html |
 * from_pdf | from_rtf | from_text | from_ics}.
 *
 * <p>The {@code file} input is a type-{@code file} value resolved through
 * {@link FilesSupport#readFileValue} (sandbox path / ContentBlock / $fileRef),
 * which enforces the 8 MiB cap. CSV/JSON/text/HTML/RTF/ICS are pure-JDK;
 * XLSX/ODS are zip+XML via {@link SpreadsheetXml} (XXE-hardened); PDF is
 * best-effort text-only via {@link PdfText}. Legacy OLE2 {@code from_xls}
 * returns a clear unsupported error. Tabular formats honor
 * {@code header_row} (first row → object keys).
 */
public final class ExtractFromFileNode implements NodeHandler {

    public static final String DEFAULT_ID = "7078cf3f-349e-4edc-b255-f7e7c9377bcc";

    private static final List<String> OPERATIONS = List.of("from_csv", "from_xlsx", "from_xls",
            "from_ods", "from_json", "from_html", "from_pdf", "from_rtf", "from_text", "from_ics");

    private final String templateId;

    public ExtractFromFileNode() {
        this(DEFAULT_ID);
    }

    public ExtractFromFileNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return CodecSupport.error("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        try {
            String op = CodecSupport.op(cfg, in);
            if (op == null) {
                return CodecSupport.error("operation is required. Accepted: " + OPERATIONS);
            }
            if (!OPERATIONS.contains(op)) {
                return CodecSupport.error("Unknown operation: " + op + ". Accepted: " + OPERATIONS);
            }
            if ("from_xls".equals(op)) {
                return CodecSupport.error("from_xls (legacy OLE2 binary) is not supported in this "
                        + "build; convert the workbook to .xlsx or .csv first");
            }
            Object fileRaw = in.get("file");
            if (fileRaw instanceof Number || fileRaw instanceof Boolean) {
                return CodecSupport.error(
                        "file must be a file-typed value (path, ContentBlock, or $fileRef).");
            }
            FilesSupport.FileBytes file;
            try {
                file = FilesSupport.readFileValue(fileRaw, "file");
            } catch (FilesSupport.Fail e) {
                return CodecSupport.error(e.getMessage());
            }
            boolean headerRow = headerRow(cfg, in);

            return switch (op) {
                case "from_csv" -> tabular(op, Csv.parse(text(file)), headerRow, null);
                case "from_xlsx" -> spreadsheet(op, SpreadsheetXml.readXlsx(file.bytes()), headerRow);
                case "from_ods" -> spreadsheet(op, SpreadsheetXml.readOds(file.bytes()), headerRow);
                case "from_json" -> fromJson(file);
                case "from_html" -> textResult(op, HtmlNode.normalize(
                        HtmlMini.text(HtmlMini.parse(text(file)))));
                case "from_pdf" -> textResult(op, HtmlNode.normalize(PdfText.extract(file.bytes())));
                case "from_rtf" -> textResult(op, rtfText(text(file)));
                case "from_ics" -> fromIcs(text(file));
                default -> textResult(op, text(file));
            };
        } catch (IllegalArgumentException e) {
            return CodecSupport.error(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return CodecSupport.error("Error: " + e.getClass().getSimpleName());
        }
    }

    // --- tabular (csv / xlsx / ods) -------------------------------------------------

    private static Map<String, Object> tabular(String op, List<List<String>> rows,
                                               boolean headerRow, String sheetName) {
        Map<String, Object> data = CodecSupport.data();
        data.put("operation", op);
        if (sheetName != null) {
            data.put("sheet", sheetName);
        }
        if (rows.isEmpty()) {
            data.put("headers", List.of());
            data.put("rows", List.of());
            data.put("row_count", 0);
            return CodecSupport.ok(data);
        }
        if (!headerRow) {
            data.put("rows", rows);
            data.put("row_count", rows.size());
            return CodecSupport.ok(data);
        }
        List<String> headers = rows.get(0);
        List<Map<String, Object>> objects = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, Object> obj = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c).isBlank() ? "column_" + (c + 1) : headers.get(c);
                obj.put(key, c < row.size() ? row.get(c) : "");
            }
            objects.add(obj);
        }
        data.put("headers", headers);
        data.put("rows", objects);
        data.put("row_count", objects.size());
        return CodecSupport.ok(data);
    }

    private static Map<String, Object> spreadsheet(String op, List<SpreadsheetXml.Sheet> sheets,
                                                   boolean headerRow) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (SpreadsheetXml.Sheet sheet : sheets) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("name", sheet.name());
            List<List<String>> rows = sheet.rows();
            if (headerRow && !rows.isEmpty()) {
                List<String> headers = rows.get(0);
                List<Map<String, Object>> objects = new ArrayList<>();
                for (int r = 1; r < rows.size(); r++) {
                    List<String> row = rows.get(r);
                    Map<String, Object> obj = new LinkedHashMap<>();
                    for (int c = 0; c < headers.size(); c++) {
                        String key = headers.get(c).isBlank() ? "column_" + (c + 1) : headers.get(c);
                        obj.put(key, c < row.size() ? row.get(c) : "");
                    }
                    objects.add(obj);
                }
                s.put("headers", headers);
                s.put("rows", objects);
                s.put("row_count", objects.size());
            } else {
                s.put("rows", rows);
                s.put("row_count", rows.size());
            }
            out.add(s);
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("operation", op);
        data.put("sheets", out);
        data.put("sheet_count", out.size());
        return CodecSupport.ok(data);
    }

    // --- scalar formats -----------------------------------------------------------------

    private static Map<String, Object> fromJson(FilesSupport.FileBytes file) {
        Object parsed = DataSupport.parseJson(text(file));
        if (parsed == null) {
            return CodecSupport.error("file is not valid JSON");
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("operation", "from_json");
        data.put("value", parsed);
        return CodecSupport.ok(data);
    }

    private static Map<String, Object> textResult(String op, String content) {
        Map<String, Object> data = CodecSupport.data();
        data.put("operation", op);
        data.put("text", content);
        data.put("char_count", content.length());
        data.put("line_count", content.isEmpty() ? 0 : content.split("\n", -1).length);
        return CodecSupport.ok(data);
    }

    // --- ics -----------------------------------------------------------------------------

    private static Map<String, Object> fromIcs(String text) {
        // Unfold continuation lines (leading space/tab), then walk components.
        List<String> lines = new ArrayList<>();
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (!lines.isEmpty() && !raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> current = null;
        for (String line : lines) {
            String upper = line.toUpperCase(java.util.Locale.ROOT);
            if (upper.startsWith("BEGIN:")) {
                if ("BEGIN:VEVENT".equals(upper.trim())) {
                    current = new LinkedHashMap<>();
                }
                continue;
            }
            if (upper.startsWith("END:")) {
                if ("END:VEVENT".equals(upper.trim()) && current != null) {
                    events.add(current);
                    current = null;
                }
                continue;
            }
            if (current == null) {
                continue;
            }
            int colon = line.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String name = line.substring(0, colon);
            int semi = name.indexOf(';');
            if (semi >= 0) {
                name = name.substring(0, semi); // drop parameters (TZID, …)
            }
            String value = line.substring(colon + 1)
                    .replace("\\n", "\n").replace("\\,", ",").replace("\\;", ";")
                    .replace("\\\\", "\\");
            current.put(name.trim().toLowerCase(java.util.Locale.ROOT), value.trim());
        }
        Map<String, Object> data = CodecSupport.data();
        data.put("operation", "from_ics");
        data.put("events", events);
        data.put("count", events.size());
        return CodecSupport.ok(data);
    }

    // --- rtf -------------------------------------------------------------------------------

    /** Minimal RTF → text: drop groups/control words, keep \'hh hex and \par breaks. */
    static String rtfText(String rtf) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        int i = 0;
        int n = rtf.length();
        while (i < n) {
            char c = rtf.charAt(i);
            if (c == '{') {
                depth++;
                i++;
                continue;
            }
            if (c == '}') {
                depth = Math.max(0, depth - 1);
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < n) {
                char e = rtf.charAt(i + 1);
                if (e == '\'' && i + 3 < n) {
                    try {
                        sb.append((char) Integer.parseInt(rtf.substring(i + 2, i + 4), 16));
                    } catch (NumberFormatException ignored) {
                        // malformed hex escape — skip
                    }
                    i += 4;
                    continue;
                }
                if (Character.isLetter(e)) {
                    int j = i + 1;
                    while (j < n && Character.isLetter(rtf.charAt(j))) {
                        j++;
                    }
                    String word = rtf.substring(i + 1, j);
                    if (j < n && (Character.isDigit(rtf.charAt(j)) || rtf.charAt(j) == '-')) {
                        j++;
                        while (j < n && Character.isDigit(rtf.charAt(j))) {
                            j++;
                        }
                    }
                    if (j < n && rtf.charAt(j) == ' ') {
                        j++; // control-word delimiter space
                    }
                    switch (word) {
                        case "par", "line" -> sb.append('\n');
                        case "tab" -> sb.append('\t');
                        default -> {
                            // all other control words carry no visible text
                        }
                    }
                    i = j;
                    continue;
                }
                // escaped brace/backslash
                sb.append(e);
                i += 2;
                continue;
            }
            if (depth > 0) {
                sb.append(c);
            }
            i++;
        }
        return HtmlNode.normalize(sb.toString());
    }

    // --- helpers ------------------------------------------------------------------------------

    private static boolean headerRow(Map<String, Object> cfg, Map<String, Object> in) {
        if (in != null && in.containsKey("header_row") && in.get("header_row") != null) {
            Object override = in.get("header_row");
            if (!(override instanceof Boolean)) {
                throw new IllegalArgumentException("header_row must be a boolean.");
            }
            return (Boolean) override;
        }
        Object c = cfg.get("header_row");
        if (c == null || String.valueOf(c).isBlank()) {
            return true;
        }
        return com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil.isTruthy(c);
    }

    private static String text(FilesSupport.FileBytes file) {
        return new String(file.bytes(), StandardCharsets.UTF_8);
    }
}
