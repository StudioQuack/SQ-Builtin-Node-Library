package com.studioquack.nodelibrary.nodes.protocol;

import com.studioquack.nodelibrary.core.DataSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * JDK-only writers/readers for Convert File: RFC 4180 CSV, ECMA-376 XLSX and
 * ODF ODS via {@code java.util.zip} + hardened XML, RFC 5545 iCalendar,
 * JSON, HTML, RTF, and plain text. Caps live on {@link ConvertFileNode}.
 */
final class ConvertFormats {

    private static final String XLSX_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main";
    private static final String REL_NS = "http://schemas.openxmlformats.org/package/2006/relationships";
    private static final String CT_NS = "http://schemas.openxmlformats.org/package/2006/content-types";
    private static final String OD_OFFICE = "urn:oasis:names:tc:opendocument:xmlns:office:1.0";
    private static final String OD_TABLE = "urn:oasis:names:tc:opendocument:xmlns:table:1.0";
    private static final String OD_TEXT = "urn:oasis:names:tc:opendocument:xmlns:text:1.0";
    private static final String OD_MIME = "application/vnd.oasis.opendocument.spreadsheet";
    private static final int MAX_ODS_REPEAT = 256;

    private ConvertFormats() {
    }

    record Sheet(String name, List<List<String>> rows) {
    }

    // --- csv (RFC 4180) -----------------------------------------------------------

    static List<List<String>> readCsv(String text) {
        List<List<String>> rows = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return rows;
        }
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean haveData = false;
        int i = 0;
        int n = text.length();
        while (i < n) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < n && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                        continue;
                    }
                    inQuotes = false;
                    i++;
                    continue;
                }
                field.append(c);
                i++;
                continue;
            }
            if (c == '"' && field.length() == 0) {
                inQuotes = true;
                haveData = true;
                i++;
                continue;
            }
            if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                haveData = true;
                i++;
                continue;
            }
            if (c == '\r') {
                i++;
                continue;
            }
            if (c == '\n') {
                row.add(field.toString());
                field.setLength(0);
                rows.add(row);
                row = new ArrayList<>();
                haveData = false;
                i++;
                continue;
            }
            field.append(c);
            haveData = true;
            i++;
        }
        if (haveData || field.length() > 0 || !row.isEmpty()) {
            row.add(field.toString());
            rows.add(row);
        }
        return rows;
    }

    static byte[] writeCsv(List<List<String>> rows, Charset cs) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sb.append("\r\n");
            }
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append(',');
                }
                sb.append(csvField(row.get(c)));
            }
        }
        if (!rows.isEmpty()) {
            sb.append("\r\n");
        }
        return sb.toString().getBytes(cs);
    }

    private static String csvField(String raw) {
        String s = raw == null ? "" : raw;
        boolean quote = s.indexOf(',') >= 0 || s.indexOf('"') >= 0
                || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0;
        if (!quote) {
            return s;
        }
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    // --- xlsx ---------------------------------------------------------------------

    static List<Sheet> readXlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzip(bytes);
        List<String> shared = sharedStrings(entries.get("xl/sharedStrings.xml"));
        List<String> names = workbookSheetNames(entries.get("xl/workbook.xml"));
        Map<Integer, String> sheetFiles = new TreeMap<>();
        for (String key : entries.keySet()) {
            if (key.matches("xl/worksheets/sheet\\d+\\.xml")) {
                sheetFiles.put(Integer.parseInt(key.replaceAll("\\D+", "")), key);
            }
        }
        List<Sheet> sheets = new ArrayList<>();
        int idx = 0;
        for (String key : sheetFiles.values()) {
            String name = idx < names.size() ? names.get(idx) : "Sheet" + (idx + 1);
            sheets.add(new Sheet(name, xlsxRows(entries.get(key), shared)));
            idx++;
        }
        return sheets;
    }

    static byte[] writeXlsx(String sheetName, List<List<String>> rows) throws IOException {
        String name = sanitizeSheetName(sheetName);
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            put(zos, "[Content_Types].xml", xlsxContentTypes());
            put(zos, "_rels/.rels", xlsxRootRels());
            put(zos, "xl/workbook.xml", xlsxWorkbook(name));
            put(zos, "xl/_rels/workbook.xml.rels", xlsxWorkbookRels());
            put(zos, "xl/worksheets/sheet1.xml", xlsxSheet(rows));
        }
        return bos.toByteArray();
    }

    private static String xlsxContentTypes() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Types xmlns=\"" + CT_NS + "\">"
                + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
                + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
                + "<Override PartName=\"/xl/workbook.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
                + "<Override PartName=\"/xl/worksheets/sheet1.xml\" "
                + "ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
                + "</Types>";
    }

    private static String xlsxRootRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + REL_NS + "\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" "
                + "Target=\"xl/workbook.xml\"/>"
                + "</Relationships>";
    }

    private static String xlsxWorkbook(String name) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<workbook xmlns=\"" + XLSX_NS + "\" "
                + "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
                + "<sheets><sheet name=\"" + xml(name) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets>"
                + "</workbook>";
    }

    private static String xlsxWorkbookRels() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<Relationships xmlns=\"" + REL_NS + "\">"
                + "<Relationship Id=\"rId1\" "
                + "Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" "
                + "Target=\"worksheets/sheet1.xml\"/>"
                + "</Relationships>";
    }

    private static String xlsxSheet(List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>");
        sb.append("<worksheet xmlns=\"").append(XLSX_NS).append("\"><sheetData>");
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            sb.append("<row r=\"").append(r + 1).append("\">");
            for (int c = 0; c < row.size(); c++) {
                String ref = colRef(c) + (r + 1);
                sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t>");
                sb.append(xml(row.get(c)));
                sb.append("</t></is></c>");
            }
            sb.append("</row>");
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    static String colRef(int index) {
        int n = index + 1;
        StringBuilder sb = new StringBuilder();
        while (n > 0) {
            int rem = (n - 1) % 26;
            sb.insert(0, (char) ('A' + rem));
            n = (n - 1) / 26;
        }
        return sb.toString();
    }

    private static List<String> sharedStrings(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        Document doc = parseXml(xml);
        NodeList si = doc.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < si.getLength(); i++) {
            StringBuilder sb = new StringBuilder();
            NodeList ts = ((Element) si.item(i)).getElementsByTagNameNS("*", "t");
            for (int j = 0; j < ts.getLength(); j++) {
                sb.append(ts.item(j).getTextContent());
            }
            out.add(sb.toString());
        }
        return out;
    }

    private static List<String> workbookSheetNames(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        Document doc = parseXml(xml);
        NodeList sheets = doc.getElementsByTagNameNS("*", "sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            out.add(((Element) sheets.item(i)).getAttribute("name"));
        }
        return out;
    }

    private static List<List<String>> xlsxRows(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        if (xml == null) {
            return rows;
        }
        Document doc = parseXml(xml);
        NodeList rowEls = doc.getElementsByTagNameNS("*", "row");
        for (int r = 0; r < rowEls.getLength(); r++) {
            Element rowEl = (Element) rowEls.item(r);
            List<String> cells = new ArrayList<>();
            NodeList cellEls = rowEl.getElementsByTagNameNS("*", "c");
            for (int c = 0; c < cellEls.getLength(); c++) {
                Element cell = (Element) cellEls.item(c);
                int col = columnIndex(cell.getAttribute("r"), cells.size());
                while (cells.size() < col) {
                    cells.add("");
                }
                cells.add(xlsxCellValue(cell, shared));
            }
            rows.add(cells);
        }
        return rows;
    }

    private static String xlsxCellValue(Element cell, List<String> shared) {
        String type = cell.getAttribute("t");
        if ("inlineStr".equals(type)) {
            NodeList ts = cell.getElementsByTagNameNS("*", "t");
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < ts.getLength(); j++) {
                sb.append(ts.item(j).getTextContent());
            }
            return sb.toString();
        }
        NodeList vs = cell.getElementsByTagNameNS("*", "v");
        if (vs.getLength() == 0) {
            return "";
        }
        String raw = vs.item(0).getTextContent();
        if ("s".equals(type)) {
            try {
                int idx = Integer.parseInt(raw.trim());
                return idx >= 0 && idx < shared.size() ? shared.get(idx) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("b".equals(type)) {
            return "1".equals(raw.trim()) ? "true" : "false";
        }
        return raw;
    }

    private static int columnIndex(String ref, int fallback) {
        if (ref == null || ref.isEmpty()) {
            return fallback;
        }
        int col = 0;
        for (int i = 0; i < ref.length(); i++) {
            char c = ref.charAt(i);
            if (!Character.isLetter(c)) {
                break;
            }
            col = col * 26 + (Character.toUpperCase(c) - 'A' + 1);
        }
        return col > 0 ? col - 1 : fallback;
    }

    // --- ods ----------------------------------------------------------------------

    static List<Sheet> readOds(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzip(bytes);
        byte[] content = entries.get("content.xml");
        List<Sheet> sheets = new ArrayList<>();
        if (content == null) {
            return sheets;
        }
        Document doc = parseXml(content);
        NodeList tables = doc.getElementsByTagNameNS("*", "table");
        for (int t = 0; t < tables.getLength(); t++) {
            Element table = (Element) tables.item(t);
            if (!"table".equals(local(table))) {
                continue;
            }
            String name = attrAny(table, "name");
            List<List<String>> rows = new ArrayList<>();
            for (Element rowEl : childElements(table, "table-row")) {
                int rowRepeat = odsRepeat(attrAny(rowEl, "number-rows-repeated"));
                List<String> cells = new ArrayList<>();
                for (Element cellEl : childElements(rowEl, null)) {
                    String tag = local(cellEl);
                    if (!"table-cell".equals(tag) && !"covered-table-cell".equals(tag)) {
                        continue;
                    }
                    int colRepeat = odsRepeat(attrAny(cellEl, "number-columns-repeated"));
                    String value = odsCellText(cellEl);
                    for (int k = 0; k < colRepeat; k++) {
                        cells.add(value);
                    }
                }
                for (int k = 0; k < rowRepeat; k++) {
                    rows.add(new ArrayList<>(cells));
                }
            }
            sheets.add(new Sheet(name == null || name.isBlank() ? "Sheet" + (t + 1) : name, rows));
        }
        return sheets;
    }

    static byte[] writeOds(String sheetName, List<List<String>> rows) throws IOException {
        String name = sanitizeSheetName(sheetName);
        String content = odsContent(name, rows);
        String manifest = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<manifest:manifest xmlns:manifest=\"urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\" "
                + "manifest:version=\"1.2\">"
                + "<manifest:file-entry manifest:full-path=\"/\" manifest:media-type=\"" + OD_MIME + "\"/>"
                + "<manifest:file-entry manifest:full-path=\"content.xml\" manifest:media-type=\"text/xml\"/>"
                + "</manifest:manifest>";
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos)) {
            stored(zos, "mimetype", OD_MIME.getBytes(StandardCharsets.US_ASCII));
            put(zos, "META-INF/manifest.xml", manifest);
            put(zos, "content.xml", content);
        }
        return bos.toByteArray();
    }

    private static String odsContent(String name, List<List<String>> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        sb.append("<office:document-content xmlns:office=\"").append(OD_OFFICE).append("\" ");
        sb.append("xmlns:table=\"").append(OD_TABLE).append("\" ");
        sb.append("xmlns:text=\"").append(OD_TEXT).append("\" office:version=\"1.2\">");
        sb.append("<office:body><office:spreadsheet>");
        sb.append("<table:table table:name=\"").append(xml(name)).append("\">");
        for (List<String> row : rows) {
            sb.append("<table:table-row>");
            for (String cell : row) {
                sb.append("<table:table-cell office:value-type=\"string\"><text:p>");
                sb.append(xml(cell));
                sb.append("</text:p></table:table-cell>");
            }
            sb.append("</table:table-row>");
        }
        sb.append("</table:table></office:spreadsheet></office:body></office:document-content>");
        return sb.toString();
    }

    private static String odsCellText(Element cell) {
        StringBuilder sb = new StringBuilder();
        NodeList ps = cell.getElementsByTagNameNS("*", "p");
        for (int i = 0; i < ps.getLength(); i++) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(ps.item(i).getTextContent());
        }
        return sb.toString();
    }

    private static int odsRepeat(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Math.min(Integer.parseInt(raw.trim()), MAX_ODS_REPEAT);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // --- json / html / rtf / text -------------------------------------------------

    static byte[] writeJson(Object value, Charset cs) {
        return studioquack.Json.write(value == null ? Map.of() : value).getBytes(cs);
    }

    static Object rowsToJson(List<List<String>> rows, boolean headerRow) {
        if (rows.isEmpty()) {
            return List.of();
        }
        if (!headerRow) {
            return rows;
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
        return objects;
    }

    static byte[] writeHtml(List<List<String>> rows, boolean headerRow, Charset cs) {
        StringBuilder sb = new StringBuilder();
        sb.append("<!DOCTYPE html><html><head><meta charset=\"").append(xml(cs.name()))
                .append("\"/></head><body><table>\n");
        for (int r = 0; r < rows.size(); r++) {
            boolean head = headerRow && r == 0;
            sb.append(head ? "<thead><tr>" : "<tr>");
            String cellTag = head ? "th" : "td";
            for (String cell : rows.get(r)) {
                sb.append('<').append(cellTag).append('>').append(xml(cell))
                        .append("</").append(cellTag).append('>');
            }
            sb.append(head ? "</tr></thead>\n" : "</tr>\n");
        }
        sb.append("</table></body></html>\n");
        return sb.toString().getBytes(cs);
    }

    static byte[] writeRtf(List<List<String>> rows, Charset cs) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\\rtf1\\ansi\\deff0\n");
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sb.append("\\par\n");
            }
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append("\\tab ");
                }
                sb.append(rtfEscape(row.get(c)));
            }
        }
        sb.append("\n}\n");
        return sb.toString().getBytes(cs);
    }

    static byte[] writeText(List<List<String>> rows, Charset cs) {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < rows.size(); r++) {
            if (r > 0) {
                sb.append('\n');
            }
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++) {
                if (c > 0) {
                    sb.append('\t');
                }
                sb.append(row.get(c) == null ? "" : row.get(c));
            }
        }
        if (!rows.isEmpty()) {
            sb.append('\n');
        }
        return sb.toString().getBytes(cs);
    }

    static String htmlToText(String html) {
        if (html == null || html.isEmpty()) {
            return "";
        }
        String s = html.replaceAll("(?is)<script[^>]*>.*?</script>", " ")
                .replaceAll("(?is)<style[^>]*>.*?</style>", " ")
                .replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n")
                .replaceAll("(?i)</tr>", "\n")
                .replaceAll("(?i)</(td|th)>", "\t")
                .replaceAll("(?s)<[^>]+>", " ");
        s = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<")
                .replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'");
        return s.replaceAll("[ \\t]+", " ").replaceAll(" *\\n *", "\n").trim();
    }

    static String rtfToText(String rtf) {
        if (rtf == null || rtf.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int i = 0;
        int n = rtf.length();
        while (i < n) {
            char c = rtf.charAt(i);
            if (c == '{') {
                i++;
                continue;
            }
            if (c == '}') {
                i++;
                continue;
            }
            if (c == '\\' && i + 1 < n) {
                char e = rtf.charAt(i + 1);
                if (e == '\'' && i + 3 < n) {
                    try {
                        sb.append((char) Integer.parseInt(rtf.substring(i + 2, i + 4), 16));
                    } catch (NumberFormatException ignored) {
                        // skip malformed hex
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
                        j++;
                    }
                    switch (word) {
                        case "par", "line" -> sb.append('\n');
                        case "tab" -> sb.append('\t');
                        default -> {
                        }
                    }
                    i = j;
                    continue;
                }
                sb.append(e);
                i += 2;
                continue;
            }
            sb.append(c);
            i++;
        }
        return sb.toString().trim();
    }

    // --- ics (RFC 5545) -----------------------------------------------------------

    static List<Map<String, Object>> readIcs(String text) {
        List<String> lines = unfoldIcs(text);
        List<Map<String, Object>> events = new ArrayList<>();
        Map<String, Object> current = null;
        for (String line : lines) {
            String upper = line.toUpperCase(Locale.ROOT);
            if (upper.startsWith("BEGIN:VEVENT")) {
                current = new LinkedHashMap<>();
                continue;
            }
            if (upper.startsWith("END:VEVENT")) {
                if (current != null) {
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
                name = name.substring(0, semi);
            }
            String value = unescapeIcs(line.substring(colon + 1));
            current.put(name.trim().toLowerCase(Locale.ROOT), value.trim());
        }
        return events;
    }

    static byte[] writeIcs(List<Map<String, Object>> events, Charset cs) {
        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//StudioQuack//Convert File//EN\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        int i = 0;
        for (Map<String, Object> ev : events) {
            i++;
            sb.append("BEGIN:VEVENT\r\n");
            String uid = first(ev, "uid", "sq-" + i + "@convert-file");
            foldIcs(sb, "UID", uid);
            String stamp = first(ev, "dtstamp", first(ev, "dtstart", "19700101T000000Z"));
            foldIcs(sb, "DTSTAMP", stamp);
            for (String key : List.of("dtstart", "dtend", "summary", "description", "location")) {
                Object v = ev.get(key);
                if (v != null && !String.valueOf(v).isBlank()) {
                    foldIcs(sb, key.toUpperCase(Locale.ROOT), String.valueOf(v));
                }
            }
            sb.append("END:VEVENT\r\n");
        }
        sb.append("END:VCALENDAR\r\n");
        return sb.toString().getBytes(cs);
    }

    static List<List<String>> eventsToRows(List<Map<String, Object>> events, boolean headerRow) {
        List<String> headers = List.of("uid", "summary", "dtstart", "dtend", "description", "location");
        List<List<String>> rows = new ArrayList<>();
        if (headerRow) {
            rows.add(new ArrayList<>(headers));
        }
        for (Map<String, Object> ev : events) {
            List<String> row = new ArrayList<>();
            for (String h : headers) {
                Object v = ev.get(h);
                row.add(v == null ? "" : String.valueOf(v));
            }
            rows.add(row);
        }
        return rows;
    }

    static List<Map<String, Object>> rowsToEvents(List<List<String>> rows, boolean headerRow) {
        List<Map<String, Object>> events = new ArrayList<>();
        if (rows.isEmpty()) {
            return events;
        }
        List<String> headers;
        int start;
        if (headerRow) {
            headers = new ArrayList<>();
            for (String h : rows.get(0)) {
                headers.add(h == null ? "" : h.trim().toLowerCase(Locale.ROOT));
            }
            start = 1;
        } else {
            headers = List.of("summary", "dtstart", "dtend", "description");
            start = 0;
        }
        for (int r = start; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            Map<String, Object> ev = new LinkedHashMap<>();
            for (int c = 0; c < headers.size(); c++) {
                String key = headers.get(c);
                if (key.isBlank()) {
                    continue;
                }
                ev.put(key, c < row.size() ? row.get(c) : "");
            }
            if (!headerRow && !row.isEmpty()) {
                ev.putIfAbsent("summary", row.get(0));
            }
            events.add(ev);
        }
        return events;
    }

    @SuppressWarnings("unchecked")
    static List<List<String>> jsonToRows(Object parsed, boolean headerRow) {
        if (parsed instanceof List<?> list) {
            if (list.isEmpty()) {
                return List.of();
            }
            Object first = list.get(0);
            if (first instanceof Map<?, ?>) {
                LinkedHashMap<String, Integer> keys = new LinkedHashMap<>();
                for (Object item : list) {
                    if (item instanceof Map<?, ?> m) {
                        for (Object k : m.keySet()) {
                            keys.putIfAbsent(String.valueOf(k), keys.size());
                        }
                    }
                }
                List<List<String>> rows = new ArrayList<>();
                if (headerRow) {
                    rows.add(new ArrayList<>(keys.keySet()));
                }
                for (Object item : list) {
                    List<String> row = new ArrayList<>();
                    Map<String, Object> m = item instanceof Map<?, ?> raw ? (Map<String, Object>) raw : Map.of();
                    for (String k : keys.keySet()) {
                        Object v = m.get(k);
                        row.add(v == null ? "" : String.valueOf(v));
                    }
                    rows.add(row);
                }
                return rows;
            }
            if (first instanceof List<?>) {
                List<List<String>> rows = new ArrayList<>();
                for (Object item : list) {
                    List<String> row = new ArrayList<>();
                    if (item instanceof List<?> cells) {
                        for (Object c : cells) {
                            row.add(c == null ? "" : String.valueOf(c));
                        }
                    } else {
                        row.add(item == null ? "" : String.valueOf(item));
                    }
                    rows.add(row);
                }
                return rows;
            }
            List<List<String>> rows = new ArrayList<>();
            if (headerRow) {
                rows.add(List.of("value"));
            }
            for (Object item : list) {
                rows.add(List.of(item == null ? "" : String.valueOf(item)));
            }
            return rows;
        }
        if (parsed instanceof Map<?, ?> map) {
            List<List<String>> rows = new ArrayList<>();
            if (headerRow) {
                rows.add(List.of("key", "value"));
            }
            map.forEach((k, v) -> rows.add(List.of(String.valueOf(k), v == null ? "" : String.valueOf(v))));
            return rows;
        }
        return List.of(List.of(parsed == null ? "" : String.valueOf(parsed)));
    }

    // --- shared -------------------------------------------------------------------

    static Sheet pickSheet(List<Sheet> sheets, String selector) {
        if (sheets == null || sheets.isEmpty()) {
            return new Sheet("Sheet1", List.of());
        }
        if (selector == null || selector.isBlank()) {
            return sheets.get(0);
        }
        String t = selector.trim();
        try {
            int idx = Integer.parseInt(t);
            if (idx >= 0 && idx < sheets.size()) {
                return sheets.get(idx);
            }
        } catch (NumberFormatException ignored) {
            // name lookup
        }
        for (Sheet s : sheets) {
            if (t.equalsIgnoreCase(s.name())) {
                return s;
            }
        }
        DataSupport.invalidInput("sheet '" + t + "' was not found");
        return sheets.get(0);
    }

    static void enforceCaps(List<List<String>> rows) {
        if (rows.size() > ConvertFileNode.MAX_ROWS) {
            DataSupport.invalidInput("row count " + rows.size() + " exceeds the "
                    + ConvertFileNode.MAX_ROWS + " row cap");
        }
        for (List<String> row : rows) {
            if (row.size() > ConvertFileNode.MAX_COLS) {
                DataSupport.invalidInput("column count " + row.size() + " exceeds the "
                        + ConvertFileNode.MAX_COLS + " column cap");
            }
            for (int i = 0; i < row.size(); i++) {
                String cell = row.get(i);
                if (cell != null && cell.length() > ConvertFileNode.MAX_CELL_CHARS) {
                    DataSupport.invalidInput("a cell exceeds the " + ConvertFileNode.MAX_CELL_CHARS
                            + " character cap");
                }
            }
        }
    }

    static String sanitizeSheetName(String raw) {
        String n = raw == null || raw.isBlank() ? "Sheet1" : raw.trim();
        n = n.replaceAll("[\\\\/*?:\\[\\]]", "_");
        return n.length() > 31 ? n.substring(0, 31) : n;
    }

    static String detectKind(String name, String mime, byte[] bytes) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        String m = mime == null ? "" : mime.toLowerCase(Locale.ROOT);
        if (n.endsWith(".xls") && !n.endsWith(".xlsx")) {
            return "xls";
        }
        if (n.endsWith(".xlsx") || m.contains("spreadsheetml")) {
            return "xlsx";
        }
        if (n.endsWith(".ods") || m.contains("opendocument.spreadsheet")) {
            return "ods";
        }
        if (n.endsWith(".csv") || m.contains("csv")) {
            return "csv";
        }
        if (n.endsWith(".json") || m.contains("json")) {
            return "json";
        }
        if (n.endsWith(".html") || n.endsWith(".htm") || m.contains("html")) {
            return "html";
        }
        if (n.endsWith(".rtf") || m.contains("rtf")) {
            return "rtf";
        }
        if (n.endsWith(".ics") || n.endsWith(".ical") || m.contains("calendar")) {
            return "ics";
        }
        if (n.endsWith(".txt") || m.startsWith("text/plain")) {
            return "text";
        }
        String head = peek(bytes);
        if (head.startsWith("PK")) {
            return n.contains("ods") ? "ods" : "xlsx";
        }
        if (head.startsWith("{") || head.startsWith("[")) {
            return "json";
        }
        if (head.toUpperCase(Locale.ROOT).contains("BEGIN:VCALENDAR")) {
            return "ics";
        }
        if (head.toUpperCase(Locale.ROOT).contains("\\RTF")) {
            return "rtf";
        }
        if (head.startsWith("<")) {
            return "html";
        }
        return "text";
    }

    private static String peek(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        int n = Math.min(bytes.length, 64);
        return new String(bytes, 0, n, StandardCharsets.ISO_8859_1);
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                if (e.isDirectory()) {
                    continue;
                }
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int read;
                long entryBytes = 0;
                while ((read = zis.read(buf)) > 0) {
                    entryBytes += read;
                    total += read;
                    if (entryBytes > ConvertFileNode.MAX_FILE_BYTES || total > ConvertFileNode.MAX_ZIP_BYTES) {
                        throw new IllegalArgumentException("spreadsheet archive exceeds the byte cap");
                    }
                    bos.write(buf, 0, read);
                }
                out.put(e.getName(), bos.toByteArray());
            }
        }
        return out;
    }

    static Document parseXml(byte[] xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        try {
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            f.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        } catch (IllegalArgumentException ignored) {
            // some JDK parsers lack these knobs
        }
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(
                new String(xml, StandardCharsets.UTF_8))));
    }

    private static void put(ZipOutputStream zos, String name, String xml) throws IOException {
        zos.putNextEntry(new ZipEntry(name));
        zos.write(xml.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private static void stored(ZipOutputStream zos, String name, byte[] data) throws IOException {
        ZipEntry e = new ZipEntry(name);
        e.setMethod(ZipEntry.STORED);
        e.setSize(data.length);
        CRC32 crc = new CRC32();
        crc.update(data);
        e.setCrc(crc.getValue());
        zos.putNextEntry(e);
        zos.write(data);
        zos.closeEntry();
    }

    private static List<Element> childElements(Element parent, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element el && (localName == null || local(el).equals(localName))) {
                out.add(el);
            }
        }
        return out;
    }

    private static String local(Element el) {
        String ln = el.getLocalName();
        return ln != null ? ln : el.getTagName().replaceAll("^.*:", "");
    }

    private static String attrAny(Element el, String localName) {
        var attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node a = attrs.item(i);
            String ln = a.getLocalName() != null ? a.getLocalName()
                    : a.getNodeName().replaceAll("^.*:", "");
            if (ln.equals(localName)) {
                return a.getNodeValue();
            }
        }
        return null;
    }

    static String xml(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '&') {
                sb.append("&amp;");
            } else if (c == '<') {
                sb.append("&lt;");
            } else if (c == '>') {
                sb.append("&gt;");
            } else if (c == '"') {
                sb.append("&quot;");
            } else if (c == 0x9 || c == 0xA || c == 0xD || c >= 0x20) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String rtfEscape(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '\\' || c == '{' || c == '}') {
                sb.append('\\').append(c);
            } else if (c == '\n') {
                sb.append("\\par ");
            } else if (c == '\t') {
                sb.append("\\tab ");
            } else if (c < 0x20 || c > 0x7e) {
                sb.append(String.format(Locale.ROOT, "\\u%d?", (int) c));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static List<String> unfoldIcs(String text) {
        List<String> lines = new ArrayList<>();
        if (text == null) {
            return lines;
        }
        for (String raw : text.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            if (!lines.isEmpty() && !raw.isEmpty() && (raw.charAt(0) == ' ' || raw.charAt(0) == '\t')) {
                lines.set(lines.size() - 1, lines.get(lines.size() - 1) + raw.substring(1));
            } else {
                lines.add(raw);
            }
        }
        return lines;
    }

    private static String unescapeIcs(String value) {
        return value.replace("\\n", "\n").replace("\\N", "\n")
                .replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\");
    }

    private static void foldIcs(StringBuilder sb, String name, String value) {
        String escaped = escapeIcs(value);
        String line = name + ":" + escaped;
        byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= 75) {
            sb.append(line).append("\r\n");
            return;
        }
        int used = 0;
        int first = 75;
        sb.append(new String(bytes, 0, first, StandardCharsets.UTF_8)).append("\r\n");
        used = first;
        while (used < bytes.length) {
            int take = Math.min(74, bytes.length - used);
            sb.append(' ').append(new String(bytes, used, take, StandardCharsets.UTF_8)).append("\r\n");
            used += take;
        }
    }

    private static String escapeIcs(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace(";", "\\;").replace(",", "\\,")
                .replace("\r\n", "\\n").replace("\n", "\\n");
    }

    private static String first(Map<String, Object> ev, String key, String fallback) {
        Object v = ev.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return fallback;
        }
        return String.valueOf(v);
    }
}
