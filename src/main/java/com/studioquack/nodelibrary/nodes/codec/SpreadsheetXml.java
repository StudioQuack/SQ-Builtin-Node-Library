package com.studioquack.nodelibrary.nodes.codec;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Dependency-free XLSX and ODS readers over {@code java.util.zip} + the JDK's
 * built-in DOM. XML parsing is hardened against XXE (DOCTYPE and external
 * entities disabled) — these are untrusted uploads.
 *
 * <p>XLSX: shared strings, inline strings, numbers-as-text, cell-ref column
 * placement. ODS: table cells with {@code text:p} content and bounded
 * column/row repetition. Both return rows as string matrices; header mapping
 * happens in the node.
 */
final class SpreadsheetXml {

    /** Per-entry and cumulative unzip caps (bomb protection). */
    private static final long MAX_ENTRY_BYTES = 8L * 1024 * 1024;
    private static final long MAX_TOTAL_BYTES = 64L * 1024 * 1024;
    /** ODS repeat attributes are unbounded in the spec; bound them here. */
    private static final int MAX_REPEAT = 256;

    private SpreadsheetXml() {
        // utility — never instantiated
    }

    record Sheet(String name, List<List<String>> rows) {
    }

    // --- xlsx --------------------------------------------------------------------

    static List<Sheet> readXlsx(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzip(bytes);
        List<String> shared = sharedStrings(entries.get("xl/sharedStrings.xml"));
        List<String> names = workbookSheetNames(entries.get("xl/workbook.xml"));
        // sheet files in numeric order: sheet1.xml, sheet2.xml, …
        Map<Integer, String> sheetFiles = new TreeMap<>();
        for (String key : entries.keySet()) {
            if (key.matches("xl/worksheets/sheet\\d+\\.xml")) {
                int num = Integer.parseInt(key.replaceAll("\\D+", ""));
                sheetFiles.put(num, key);
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

    private static List<String> sharedStrings(byte[] xml) throws Exception {
        List<String> out = new ArrayList<>();
        if (xml == null) {
            return out;
        }
        Document doc = parse(xml);
        NodeList si = doc.getElementsByTagNameNS("*", "si");
        for (int i = 0; i < si.getLength(); i++) {
            // concat all <t> descendants (rich runs split text across them)
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
        Document doc = parse(xml);
        NodeList sheets = doc.getElementsByTagNameNS("*", "sheet");
        for (int i = 0; i < sheets.getLength(); i++) {
            Element el = (Element) sheets.item(i);
            out.add(el.getAttribute("name"));
        }
        return out;
    }

    private static List<List<String>> xlsxRows(byte[] xml, List<String> shared) throws Exception {
        List<List<String>> rows = new ArrayList<>();
        if (xml == null) {
            return rows;
        }
        Document doc = parse(xml);
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

    /** Column index from a cell ref like {@code B7}; fallback when absent. */
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

    // --- ods ------------------------------------------------------------------------

    static List<Sheet> readOds(byte[] bytes) throws Exception {
        Map<String, byte[]> entries = unzip(bytes);
        byte[] content = entries.get("content.xml");
        List<Sheet> sheets = new ArrayList<>();
        if (content == null) {
            return sheets;
        }
        Document doc = parse(content);
        NodeList tables = doc.getElementsByTagNameNS("*", "table");
        for (int t = 0; t < tables.getLength(); t++) {
            Element table = (Element) tables.item(t);
            if (!"table".equals(local(table))) {
                continue;
            }
            String name = attrAny(table, "name");
            List<List<String>> rows = new ArrayList<>();
            for (Element rowEl : childElements(table, "table-row")) {
                int rowRepeat = repeat(attrAny(rowEl, "number-rows-repeated"));
                List<String> cells = new ArrayList<>();
                for (Element cellEl : childElements(rowEl, null)) {
                    String tag = local(cellEl);
                    if (!"table-cell".equals(tag) && !"covered-table-cell".equals(tag)) {
                        continue;
                    }
                    int colRepeat = repeat(attrAny(cellEl, "number-columns-repeated"));
                    String value = cellText(cellEl);
                    for (int k = 0; k < colRepeat; k++) {
                        cells.add(value);
                    }
                }
                for (int k = 0; k < rowRepeat; k++) {
                    rows.add(new ArrayList<>(cells));
                }
            }
            sheets.add(new Sheet(name == null || name.isBlank() ? "Table" + (t + 1) : name, rows));
        }
        return sheets;
    }

    private static String cellText(Element cell) {
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

    private static int repeat(String raw) {
        if (raw == null || raw.isBlank()) {
            return 1;
        }
        try {
            return Math.min(Integer.parseInt(raw.trim()), MAX_REPEAT);
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    // --- shared xml/zip plumbing ---------------------------------------------------------

    /** Hardened DOM parse: no DOCTYPE, no external entities, no XInclude. */
    static Document parse(byte[] xml) throws Exception {
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
            // some JDK parsers lack these knobs; the features above still apply
        }
        return f.newDocumentBuilder().parse(new InputSource(new StringReader(
                new String(xml, java.nio.charset.StandardCharsets.UTF_8))));
    }

    private static Map<String, byte[]> unzip(byte[] bytes) throws Exception {
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
                    if (entryBytes > MAX_ENTRY_BYTES || total > MAX_TOTAL_BYTES) {
                        throw new IllegalArgumentException("spreadsheet archive exceeds the byte cap");
                    }
                    bos.write(buf, 0, read);
                }
                out.put(e.getName(), bos.toByteArray());
            }
        }
        return out;
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

    /** Attribute lookup by local name across namespaces (office:, table:, …). */
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
}
