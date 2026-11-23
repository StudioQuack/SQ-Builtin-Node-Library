package com.studioquack.nodelibrary.nodes.transform;

import com.studioquack.nodelibrary.core.NodeHandler;
import com.studioquack.nodelibrary.core.DataSupport;
import studioquack.StudioQuackHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * XML node ({@code nodes/xml}) — JSON ↔ XML 1.0 conversion.
 *
 * <p>{@code json_to_xml}: object entries become child elements of {@code root_name}
 * (default {@code root}); keys prefixed with {@code attr_key} (default {@code @})
 * become attributes; {@code #text} is text content; arrays repeat the element;
 * {@code cdata=true} wraps text in CDATA. A non-object {@code item} (fragment) wraps
 * under {@code property} (default {@code item}).
 *
 * <p>{@code xml_to_json}: parses with a hardened DOM factory — DOCTYPE declarations
 * are disallowed and external entities/XInclude are off (XXE). Attributes come back as
 * {@code @name}, mixed text as {@code #text}, repeated siblings as arrays. The result
 * is keyed by the root element name.
 */
public final class XmlNode implements NodeHandler {

    public static final String DEFAULT_ID = "00805b5d-ba07-4a28-a725-171609d4abed";

    private static final int MAX_XML_CHARS = 2_000_000;

    private final String templateId;

    public XmlNode() {
        this(DEFAULT_ID);
    }

    public XmlNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank()) ? DEFAULT_ID : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        try {
            if (request == null) {
                return DataSupport.invalidInput("request body must be a JSON object");
            }
            Map<String, Object> cfg = DataSupport.readConfig(request);
            Map<String, Object> in = DataSupport.overlayConfig(
                    DataSupport.readInput(request, helper), cfg, "item", "xml");
            String op = TransformSupport.operation(cfg, in);

            return switch (op) {
                case "json_to_xml" -> jsonToXml(cfg, in);
                case "xml_to_json" -> xmlToJson(cfg, in);
                default -> DataSupport.invalidInput("unknown operation: " + (op.isEmpty() ? "<missing>" : op));
            };
        } catch (IllegalArgumentException e) {
            return DataSupport.fail(e.getMessage());
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.fail("xml failed");
        }
    }

    // ---------- json_to_xml ----------

    private Map<String, Object> jsonToXml(Map<String, Object> cfg, Map<String, Object> in) {
        Object item = in.get("item");
        if (item instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    item = studioquack.Json.parse(t);
                } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
                    return DataSupport.invalidInput("item must be valid JSON.");
                }
            }
        }
        if (item == null) {
            return DataSupport.invalidInput("item is required for json_to_xml.");
        }
        String rootName = DataSupport.cfgStr(cfg, "root_name", "root");
        String attrKey = DataSupport.cfgStr(cfg, "attr_key", "@");
        String property = DataSupport.cfgStr(cfg, "property", "item");
        boolean cdata = TransformSupport.cfgBool(cfg, "cdata", false);
        if (!isXmlName(rootName)) {
            return DataSupport.invalidInput("root_name is not a valid XML name.");
        }

        StringBuilder sb = new StringBuilder();
        if (item instanceof Map<?, ?> m) {
            sb.append('<').append(rootName);
            StringBuilder children = new StringBuilder();
            appendObjectBody(m, attrKey, cdata, sb, children);
            sb.append('>');
            sb.append(children);
            sb.append("</").append(rootName).append('>');
        } else {
            // fragment: wrap under the configured property name
            sb.append('<').append(rootName).append('>');
            appendValue(sb, property, item, attrKey, cdata);
            sb.append("</").append(rootName).append('>');
        }
        return TransformSupport.ok(Map.of("xml", sb.toString()));
    }

    /** Attributes go to {@code head}, elements/text to {@code children}. */
    private static void appendObjectBody(Map<?, ?> m, String attrKey, boolean cdata,
                                         StringBuilder head, StringBuilder children) {
        for (Map.Entry<?, ?> e : m.entrySet()) {
            String key = String.valueOf(e.getKey());
            Object v = e.getValue();
            if (attrKey != null && !attrKey.isEmpty() && key.startsWith(attrKey) && key.length() > attrKey.length()) {
                String attrName = key.substring(attrKey.length());
                if (isXmlName(attrName) && !(v instanceof Map) && !(v instanceof List)) {
                    head.append(' ').append(attrName).append("=\"")
                            .append(escapeAttr(v == null ? "" : String.valueOf(v))).append('"');
                }
                continue;
            }
            if ("#text".equals(key)) {
                appendText(children, v == null ? "" : String.valueOf(v), cdata);
                continue;
            }
            appendValue(children, key, v, attrKey, cdata);
        }
    }

    private static void appendValue(StringBuilder sb, String name, Object v, String attrKey, boolean cdata) {
        String elementName = isXmlName(name) ? name : "item";
        if (v instanceof List<?> list) {
            for (Object el : list) {
                appendValue(sb, elementName, el, attrKey, cdata);
            }
            return;
        }
        if (v instanceof Map<?, ?> m) {
            sb.append('<').append(elementName);
            StringBuilder children = new StringBuilder();
            appendObjectBody(m, attrKey, cdata, sb, children);
            sb.append('>').append(children).append("</").append(elementName).append('>');
            return;
        }
        sb.append('<').append(elementName).append('>');
        appendText(sb, v == null ? "" : String.valueOf(v), cdata);
        sb.append("</").append(elementName).append('>');
    }

    private static void appendText(StringBuilder sb, String text, boolean cdata) {
        if (cdata) {
            // split the CDATA terminator so content can never break out of the section
            sb.append("<![CDATA[").append(text.replace("]]>", "]]]]><![CDATA[>")).append("]]>");
        } else {
            sb.append(escapeText(text));
        }
    }

    // ---------- xml_to_json ----------

    private Map<String, Object> xmlToJson(Map<String, Object> cfg, Map<String, Object> in) {
        String xml = DataSupport.inStr(in, "xml", null);
        if (xml == null) {
            return DataSupport.invalidInput("xml is required for xml_to_json.");
        }
        if (xml.length() > MAX_XML_CHARS) {
            return DataSupport.invalidInput("xml exceeds the 2000000 character cap.");
        }
        String attrKey = DataSupport.cfgStr(cfg, "attr_key", "@");
        Document doc;
        try {
            doc = secureBuilder().parse(new InputSource(new StringReader(xml)));
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return DataSupport.invalidInput("invalid XML document.");
        }
        Element root = doc.getDocumentElement();
        if (root == null) {
            return DataSupport.invalidInput("invalid XML document: no root element.");
        }
        return TransformSupport.ok(Map.of(root.getTagName(), elementToJson(root, attrKey)));
    }

    /**
     * XXE-hardened factory: DOCTYPE is rejected outright, so no external or parameter
     * entity can ever be resolved; XInclude and entity expansion are off as well.
     */
    static DocumentBuilderFactory secureFactory() {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        try {
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        } catch (Exception ignored) {
            // a parser without this feature must still not fetch entities
        }
        try {
            f.setFeature("http://xml.org/sax/features/external-general-entities", false);
        } catch (Exception ignored) {
        }
        try {
            f.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        } catch (Exception ignored) {
        }
        try {
            f.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception ignored) {
        }
        f.setXIncludeAware(false);
        f.setExpandEntityReferences(false);
        f.setNamespaceAware(false);
        return f;
    }

    private static DocumentBuilder secureBuilder() throws Exception {
        DocumentBuilder b = secureFactory().newDocumentBuilder();
        // never surface parser internals; never resolve anything over the network
        b.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return b;
    }

    /** Element → text when it has neither attributes nor element children, else a map. */
    private static Object elementToJson(Element el, String attrKey) {
        Map<String, Object> obj = new LinkedHashMap<>();
        NamedNodeMap attrs = el.getAttributes();
        for (int i = 0; i < attrs.getLength(); i++) {
            Node attr = attrs.item(i);
            obj.put(attrKey + attr.getNodeName(), attr.getNodeValue());
        }
        Map<String, List<Object>> children = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        NodeList nodes = el.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                Element child = (Element) n;
                children.computeIfAbsent(child.getTagName(), k -> new ArrayList<>())
                        .add(elementToJson(child, attrKey));
            } else if (n.getNodeType() == Node.TEXT_NODE || n.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(n.getNodeValue());
            }
        }
        for (Map.Entry<String, List<Object>> e : children.entrySet()) {
            obj.put(e.getKey(), e.getValue().size() == 1 ? e.getValue().get(0) : e.getValue());
        }
        String t = text.toString().trim();
        if (obj.isEmpty()) {
            return t;
        }
        if (!t.isEmpty()) {
            obj.put("#text", t);
        }
        return obj;
    }

    // ---------- escaping / names ----------

    static String escapeText(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    static String escapeAttr(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&apos;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Conservative XML name check (letters, digits, '_', '-', '.', ':'; no leading digit/-/.). */
    static boolean isXmlName(String s) {
        if (s == null || s.isEmpty()) {
            return false;
        }
        char first = s.charAt(0);
        if (!Character.isLetter(first) && first != '_' && first != ':') {
            return false;
        }
        for (int i = 1; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!Character.isLetterOrDigit(c) && c != '_' && c != '-' && c != '.' && c != ':') {
                return false;
            }
        }
        return true;
    }
}
