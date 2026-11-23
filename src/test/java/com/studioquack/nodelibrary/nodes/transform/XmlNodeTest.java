package com.studioquack.nodelibrary.nodes.transform;

import org.junit.jupiter.api.Test;
import studioquack.StudioQuackError;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** XML node: json_to_xml / xml_to_json, XXE hardening. */
class XmlNodeTest {

    private final XmlNode node = new XmlNode();

    private static Map<String, Object> requestWithConfig(Object... shortCodeThenValue) {
        List<Object> params = new ArrayList<>();
        for (int i = 0; i + 1 < shortCodeThenValue.length; i += 2) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("shortCode", shortCodeThenValue[i]);
            row.put("value", shortCodeThenValue[i + 1]);
            params.add(row);
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("executionContext", Map.of("currentNode",
                Map.of("data", Map.of("configurationParameters", params))));
        return request;
    }

    private static Map<String, Object> withInput(Map<String, Object> request, Object inputValue) {
        request.put("inputVariables", Map.of("input", inputValue));
        return request;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> output(Map<String, Object> ports) {
        return (Map<String, Object>) ports.get("output");
    }

    private static StudioQuackError thrown(java.util.function.Supplier<Map<String, Object>> call) {
        return assertThrows(StudioQuackError.class, call::get);
    }

    private static Object dataOf(Map<String, Object> ports) {
        assertEquals("ok", output(ports).get("status"), "expected ok: " + ports);
        return output(ports).get("data");
    }

    @SuppressWarnings("unchecked")
    private static String xmlOf(Map<String, Object> ports) {
        return (String) ((Map<String, Object>) dataOf(ports)).get("xml");
    }

    @Test
    void jsonToXmlBasicObject() {
        // LinkedHashMap: Map.of iteration order is unspecified — XML order assertions need insertion order
        Map<String, Object> item = new java.util.LinkedHashMap<>();
        item.put("name", "Ada");
        item.put("age", 36);
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml"),
                Map.of("item", item));
        assertEquals("<root><name>Ada</name><age>36</age></root>", xmlOf(node.handle(request, null)));
    }

    @Test
    void jsonToXmlAttributesAndCustomRoot() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml", "root_name", "person"),
                Map.of("item", Map.of("@id", "1", "name", "Ada")));
        assertEquals("<person id=\"1\"><name>Ada</name></person>", xmlOf(node.handle(request, null)));
    }

    @Test
    void jsonToXmlRepeatsElementsForArrays() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml"),
                Map.of("item", Map.of("i", List.of(1, 2))));
        assertEquals("<root><i>1</i><i>2</i></root>", xmlOf(node.handle(request, null)));
    }

    @Test
    void jsonToXmlEscapesSpecials() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml"),
                Map.of("item", Map.of("t", "a<b>&\"c\"")));
        assertEquals("<root><t>a&lt;b&gt;&amp;\"c\"</t></root>", xmlOf(node.handle(request, null)));
    }

    @Test
    void jsonToXmlCdataWrapsText() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml", "cdata", "true"),
                Map.of("item", Map.of("t", "<x>")));
        assertEquals("<root><t><![CDATA[<x>]]></t></root>", xmlOf(node.handle(request, null)));
    }

    @Test
    void jsonToXmlFragmentWrapsUnderProperty() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml", "property", "row"),
                Map.of("item", List.of(1, 2)));
        assertEquals("<root><row>1</row><row>2</row></root>", xmlOf(node.handle(request, null)));
    }

    @Test
    void xmlToJsonBasicDocument() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xml_to_json"),
                Map.of("xml", "<root><name>Ada</name></root>"));
        assertEquals(Map.of("root", Map.of("name", "Ada")), dataOf(node.handle(request, null)));
    }

    @Test
    void xmlToJsonAttributesAndRepeatedSiblings() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xml_to_json"),
                Map.of("xml", "<root id=\"1\"><i>a</i><i>b</i></root>"));
        assertEquals(Map.of("root", Map.of("@id", "1", "i", List.of("a", "b"))),
                dataOf(node.handle(request, null)));
    }

    @Test
    void xmlToJsonRoundTripsJsonToXml() {
        Map<String, Object> toXml = withInput(
                requestWithConfig("operation", "json_to_xml"),
                Map.of("item", Map.of("name", "Ada", "tags", List.of("x", "y"))));
        String xml = xmlOf(node.handle(toXml, null));
        Map<String, Object> back = withInput(
                requestWithConfig("operation", "xml_to_json"),
                Map.of("xml", xml));
        assertEquals(Map.of("root", Map.of("name", "Ada", "tags", List.of("x", "y"))),
                dataOf(node.handle(back, null)));
    }

    @Test
    void xxeDoctypeIsRejected() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xml_to_json"),
                Map.of("xml", "<!DOCTYPE foo [<!ENTITY xxe SYSTEM \"file:///etc/passwd\">]>"
                        + "<foo>&xxe;</foo>"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("invalid XML"));
    }

    @Test
    void malformedXmlIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xml_to_json"),
                Map.of("xml", "<root><unclosed></root>"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("invalid XML"));
    }

    @Test
    void missingItemIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("item"));
    }

    @Test
    void missingXmlIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xml_to_json"), Map.of());
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("xml"));
    }

    @Test
    void invalidRootNameIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "json_to_xml", "root_name", "1bad"),
                Map.of("item", Map.of("a", 1)));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("root_name"));
    }

    @Test
    void unknownOperationIsAnError() {
        Map<String, Object> request = withInput(
                requestWithConfig("operation", "xpath"),
                Map.of("xml", "<root/>"));
        StudioQuackError e = thrown(() -> node.handle(request, null));
        assertEquals("INVALID_INPUT", e.errorCode());
        assertTrue(e.getMessage().contains("unknown operation"));
    }
}
