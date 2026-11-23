package com.studioquack.nodelibrary.nodes.localllm;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Local LLM node_definition.json: inputFiles / outputFiles / result.output_files. */
class LocalLlmSchemaTest {

    private static final List<String> FILE_CHILDREN =
            List.of("kind", "mime_type", "source", "data", "name");

    @Test
    void definitionIsValidJsonWithFilePorts() throws Exception {
        Path path = Path.of("nodes/local-llm/node_definition.json");
        Map<String, Object> def = Json.asMap(Json.parse(Files.readString(path)));
        assertEquals("local.llm.provider", def.get("bpmnType"));

        Map<String, Object> input = port(def, "llm_input");
        Map<String, Object> inputFiles = attr(schema(input), "inputFiles");
        assertFileAttr(inputFiles, "User-uploaded files and voice clips for the model.");

        Map<String, Object> output = port(def, "llm_output");
        Map<String, Object> outputFiles = attr(schema(output), "outputFiles");
        assertFileAttr(outputFiles, "files produced by the model");

        Map<String, Object> result = attr(schema(output), "result");
        Map<String, Object> nested = attr(children(result), "output_files");
        assertEquals("file", nested.get("type"));
        assertFileChildren(nested);
        assertFalse(nested.containsKey("_id"), "do not invent _ids when siblings omit them");
        assertFalse(inputFiles.containsKey("_id"));
        assertFalse(outputFiles.containsKey("_id"));
    }

    private static void assertFileAttr(Map<String, Object> attr, String descriptionSnippet) {
        assertNotNull(attr, "missing file attribute");
        assertEquals("file", attr.get("type"));
        assertTrue(String.valueOf(attr.get("description")).toLowerCase()
                .contains(descriptionSnippet.toLowerCase()), String.valueOf(attr.get("description")));
        assertFileChildren(attr);
    }

    private static void assertFileChildren(Map<String, Object> attr) {
        List<Object> kids = children(attr);
        assertEquals(FILE_CHILDREN.size(), kids.size(), "expected the standard 5 file children");
        for (int i = 0; i < FILE_CHILDREN.size(); i++) {
            Map<String, Object> child = Json.asMap(kids.get(i));
            assertEquals(FILE_CHILDREN.get(i), child.get("shortCode"));
            assertEquals("string", child.get("type"));
            assertTrue(child.containsKey("label"));
            assertTrue(child.containsKey("required"));
            assertTrue(child.containsKey("value"));
            assertTrue(child.containsKey("description"));
            assertTrue(child.containsKey("children"));
            assertFalse(child.containsKey("code"), "repo convention is shortCode, not code");
            assertFalse(child.containsKey("_id"));
        }
        assertEquals("base64", Json.asMap(kids.get(2)).get("value"));
    }

    private static Map<String, Object> port(Map<String, Object> def, String value) {
        for (Object raw : Json.asList(def.get("ports"))) {
            Map<String, Object> p = Json.asMap(raw);
            if (value.equals(p.get("value"))) {
                return p;
            }
        }
        throw new AssertionError("missing port " + value);
    }

    private static List<Object> schema(Map<String, Object> port) {
        return Json.asList(port.get("schema"));
    }

    private static List<Object> children(Map<String, Object> attr) {
        return Json.asList(attr.get("children"));
    }

    private static Map<String, Object> attr(List<Object> list, String shortCode) {
        for (Object raw : list) {
            Map<String, Object> a = Json.asMap(raw);
            if (shortCode.equals(a.get("shortCode"))) {
                return a;
            }
        }
        throw new AssertionError("missing shortCode " + shortCode);
    }
}
