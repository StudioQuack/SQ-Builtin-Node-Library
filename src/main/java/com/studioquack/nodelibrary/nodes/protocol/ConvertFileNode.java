package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.nodes.files.FilesSupport;
import com.studioquack.nodelibrary.nodes.testemulator.EmulatorUtil;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converts a platform file ref to CSV, XLSX, ODS, JSON, HTML, RTF, text, or ICS.
 *
 * <p>Source bytes are resolved through {@link FilesSupport#readFileValue}. The
 * produced ContentBlock is published on {@code file} and {@code outputFiles}
 * (same list shape OfficialApiNode.outputFilesOf reads). Legacy OLE2
 * {@code to_xls} is not offered — it cannot be produced robustly on the JDK
 * alone. Caps: {@link #MAX_FILE_BYTES}, {@link #MAX_ROWS}.
 */
public final class ConvertFileNode implements NodeHandler {

    public static final String DEFAULT_ID = "a40a264c-c9ae-45d2-b591-665576cbd588";

    /** Decoded-byte ceiling for source and result (matches {@link FilesSupport#MAX_FILE_BYTES}). */
    public static final int MAX_FILE_BYTES = FilesSupport.MAX_FILE_BYTES;
    /** Cumulative unzip ceiling for spreadsheet archives. */
    public static final long MAX_ZIP_BYTES = 64L * 1024 * 1024;
    public static final int MAX_ROWS = 20_000;
    public static final int MAX_COLS = 256;
    public static final int MAX_CELL_CHARS = 32_768;
    public static final int MAX_EVENTS = 5_000;

    private static final List<String> OPERATIONS = List.of(
            "to_csv", "to_xlsx", "to_ods", "to_json", "to_html", "to_rtf", "to_text", "to_ics");

    private final String templateId;

    public ConvertFileNode() {
        this(DEFAULT_ID);
    }

    public ConvertFileNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (request == null) {
            return DataSupport.invalidInput("request body must be a JSON object");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        Map<String, Object> in = DataSupport.readInput(request, helper);
        DataSupport.cfgInt(cfg, "timeout_seconds", 25);

        String op = operation(cfg, in);
        if (op == null) {
            return DataSupport.invalidInput("operation is required. Accepted: " + OPERATIONS);
        }
        if ("to_xls".equals(op)) {
            return DataSupport.invalidInput("to_xls (legacy OLE2 binary) is not supported; "
                    + "use to_xlsx, to_ods, or to_csv");
        }
        if (!OPERATIONS.contains(op)) {
            return DataSupport.invalidInput("Unknown operation: " + op + ". Accepted: " + OPERATIONS);
        }

        FilesSupport.FileBytes file;
        try {
            file = FilesSupport.readFileValue(in.get("file"), "file");
        } catch (FilesSupport.Fail e) {
            return DataSupport.invalidInput(e.getMessage());
        }
        if (file.bytes().length > MAX_FILE_BYTES) {
            return DataSupport.invalidInput("file is " + file.bytes().length + " bytes, above the "
                    + MAX_FILE_BYTES + " byte node limit");
        }

        Charset charset = charset(firstNonBlank(DataSupport.inStr(in, "encoding", null),
                DataSupport.cfgStr(cfg, "encoding", "UTF-8")));
        boolean headerRow = headerRow(cfg, in);
        String sheetSel = sheetSelection(in, cfg);
        String fileName = firstNonBlank(DataSupport.inStr(in, "file_name", null),
                DataSupport.cfgStr(cfg, "file_name", null));

        try {
            Parsed src = parse(file, charset, sheetSel, headerRow);
            Written out = write(op, src, charset, headerRow, fileName);
            if (out.bytes().length > MAX_FILE_BYTES) {
                return DataSupport.invalidInput("converted file is " + out.bytes().length
                        + " bytes, above the " + MAX_FILE_BYTES + " byte node limit");
            }
            Map<String, Object> block = FilesSupport.fileBlock(out.name(), out.mime(), out.bytes());
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("operation", op);
            data.put("format", out.format());
            data.put("name", out.name());
            data.put("size", out.bytes().length);
            data.put("row_count", src.rows().size());
            if (src.sheet() != null) {
                data.put("sheet", src.sheet());
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("status", "ok");
            body.put("data", data);
            body.put("file", block);
            body.put("outputFiles", List.of(block));
            return DataSupport.ports(body);
        } catch (StudioQuackError e) {
            throw e;
        } catch (IllegalArgumentException e) {
            return DataSupport.invalidInput(e.getMessage());
        } catch (Exception e) {
            return DataSupport.fail("convert failed: " + e.getClass().getSimpleName());
        }
    }

    private static Parsed parse(FilesSupport.FileBytes file, Charset charset, String sheetSel,
                                boolean headerRow) throws Exception {
        String kind = ConvertFormats.detectKind(file.name(), file.mimeType(), file.bytes());
        if ("xls".equals(kind)) {
            DataSupport.invalidInput("legacy OLE2 .xls cannot be read in this build; convert to .xlsx or .csv first");
        }
        String text = new String(file.bytes(), charset);
        return switch (kind) {
            case "xlsx" -> fromSheets(ConvertFormats.readXlsx(file.bytes()), sheetSel);
            case "ods" -> fromSheets(ConvertFormats.readOds(file.bytes()), sheetSel);
            case "csv" -> table("csv", null, ConvertFormats.readCsv(text));
            case "json" -> fromJson(text, headerRow);
            case "ics" -> fromIcs(text, headerRow);
            case "html" -> fromText("html", ConvertFormats.htmlToText(text));
            case "rtf" -> fromText("rtf", ConvertFormats.rtfToText(text));
            default -> fromText("text", text);
        };
    }

    private static Parsed fromSheets(List<ConvertFormats.Sheet> sheets, String sheetSel) {
        ConvertFormats.Sheet sheet = ConvertFormats.pickSheet(sheets, sheetSel);
        ConvertFormats.enforceCaps(sheet.rows());
        return new Parsed("spreadsheet", sheet.name(), sheet.rows(), null, List.of());
    }

    private static Parsed table(String kind, String sheet, List<List<String>> rows) {
        ConvertFormats.enforceCaps(rows);
        return new Parsed(kind, sheet, rows, null, List.of());
    }

    private static Parsed fromJson(String text, boolean headerRow) {
        Object parsed = DataSupport.parseJson(text);
        if (parsed == null) {
            DataSupport.invalidInput("file is not valid JSON");
        }
        List<List<String>> rows = ConvertFormats.jsonToRows(parsed, headerRow);
        ConvertFormats.enforceCaps(rows);
        return new Parsed("json", null, rows, parsed, List.of());
    }

    private static Parsed fromIcs(String text, boolean headerRow) {
        List<Map<String, Object>> events = ConvertFormats.readIcs(text);
        if (events.size() > MAX_EVENTS) {
            DataSupport.invalidInput("event count " + events.size() + " exceeds the " + MAX_EVENTS + " event cap");
        }
        List<List<String>> rows = ConvertFormats.eventsToRows(events, headerRow);
        ConvertFormats.enforceCaps(rows);
        return new Parsed("ics", null, rows, Map.of("events", events), events);
    }

    private static Parsed fromText(String kind, String text) {
        List<List<String>> csv = ConvertFormats.readCsv(text);
        if (csv.size() > 1 || (csv.size() == 1 && csv.get(0).size() > 1)) {
            ConvertFormats.enforceCaps(csv);
            return new Parsed(kind, null, csv, text, List.of());
        }
        List<List<String>> lines = new ArrayList<>();
        for (String line : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            lines.add(List.of(line));
        }
        if (lines.size() == 1 && lines.get(0).get(0).isEmpty() && text.isEmpty()) {
            lines = List.of();
        }
        ConvertFormats.enforceCaps(lines);
        return new Parsed(kind, null, lines, text, List.of());
    }

    private static Written write(String op, Parsed src, Charset charset, boolean headerRow,
                                 String fileName) throws Exception {
        return switch (op) {
            case "to_csv" -> new Written("csv", name(fileName, src, "csv"),
                    "text/csv", ConvertFormats.writeCsv(src.rows(), charset));
            case "to_xlsx" -> new Written("xlsx", name(fileName, src, "xlsx"),
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ConvertFormats.writeXlsx(src.sheetOrDefault(), src.rows()));
            case "to_ods" -> new Written("ods", name(fileName, src, "ods"),
                    "application/vnd.oasis.opendocument.spreadsheet",
                    ConvertFormats.writeOds(src.sheetOrDefault(), src.rows()));
            case "to_json" -> new Written("json", name(fileName, src, "json"),
                    "application/json", ConvertFormats.writeJson(jsonValue(src, headerRow), charset));
            case "to_html" -> new Written("html", name(fileName, src, "html"),
                    "text/html", ConvertFormats.writeHtml(src.rows(), headerRow, charset));
            case "to_rtf" -> new Written("rtf", name(fileName, src, "rtf"),
                    "application/rtf", ConvertFormats.writeRtf(src.rows(), charset));
            case "to_text" -> new Written("text", name(fileName, src, "txt"),
                    "text/plain", ConvertFormats.writeText(src.rows(), charset));
            case "to_ics" -> new Written("ics", name(fileName, src, "ics"),
                    "text/calendar", ConvertFormats.writeIcs(icsEvents(src, headerRow), charset));
            default -> {
                DataSupport.invalidInput("Unknown operation: " + op);
                yield new Written("bin", "out.bin", "application/octet-stream", new byte[0]);
            }
        };
    }

    private static Object jsonValue(Parsed src, boolean headerRow) {
        if (src.json() != null) {
            return src.json();
        }
        if (!src.events().isEmpty()) {
            return Map.of("events", src.events());
        }
        return ConvertFormats.rowsToJson(src.rows(), headerRow);
    }

    private static List<Map<String, Object>> icsEvents(Parsed src, boolean headerRow) {
        if (!src.events().isEmpty()) {
            return src.events();
        }
        List<Map<String, Object>> events = ConvertFormats.rowsToEvents(src.rows(), headerRow);
        if (events.size() > MAX_EVENTS) {
            DataSupport.invalidInput("event count " + events.size() + " exceeds the " + MAX_EVENTS + " event cap");
        }
        return events;
    }

    private static String name(String fileName, Parsed src, String ext) {
        if (fileName != null && !fileName.isBlank()) {
            String t = fileName.trim();
            int slash = Math.max(t.lastIndexOf('/'), t.lastIndexOf('\\'));
            if (slash >= 0) {
                t = t.substring(slash + 1);
            }
            if (!t.isBlank()) {
                return t;
            }
        }
        String base = src.sheet() != null && !src.sheet().isBlank() ? src.sheet() : "converted";
        return ConvertFormats.sanitizeSheetName(base) + "." + ext;
    }

    private static String operation(Map<String, Object> cfg, Map<String, Object> in) {
        String v = DataSupport.inStr(in, "operation", null);
        if (v == null) {
            v = DataSupport.cfgStr(cfg, "operation", null);
        }
        return v == null ? null : v.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean headerRow(Map<String, Object> cfg, Map<String, Object> in) {
        Object override = in.get("header_row");
        if (override != null && !String.valueOf(override).isBlank()) {
            return EmulatorUtil.isTruthy(override);
        }
        Object c = cfg.get("header_row");
        if (c == null || String.valueOf(c).isBlank()) {
            return true;
        }
        return EmulatorUtil.isTruthy(c);
    }

    private static Charset charset(String name) {
        try {
            return Charset.forName(name == null || name.isBlank() ? "UTF-8" : name.trim());
        } catch (Exception e) {
            DataSupport.invalidInput("Unknown encoding: " + name);
            return StandardCharsets.UTF_8;
        }
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    static String sheetSelection(Map<String, Object> in, Map<String, Object> cfg) {
        Object raw = in == null ? null : in.get("sheet");
        if (raw == null && cfg != null) {
            raw = cfg.get("sheet");
        }
        if (raw == null) {
            return null;
        }
        if (raw instanceof Number n) {
            return String.valueOf(n.intValue());
        }
        String s = String.valueOf(raw).trim();
        return s.isEmpty() ? null : s;
    }

    private record Parsed(String kind, String sheet, List<List<String>> rows, Object json,
                          List<Map<String, Object>> events) {
        String sheetOrDefault() {
            return sheet == null || sheet.isBlank() ? "Sheet1" : sheet;
        }
    }

    private record Written(String format, String name, String mime, byte[] bytes) {
    }
}
