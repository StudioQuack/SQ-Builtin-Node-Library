package com.studioquack.nodelibrary.spec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import studioquack.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Official-API LLM nodes expose {@code llm_input.inputFiles} and
 * {@code llm_output.result}/{@code outputFiles} so PortSchemaFilter keeps the
 * assistant envelope and file lists. Scoped to the 22 spec-driven LLM keys —
 * not the full catalog loop.
 */
class OfficialApiLlmFileSchemaTest {

    private static final Path NODES = Path.of("nodes");
    private static final List<String> FILE_CHILDREN =
            List.of("kind", "mime_type", "source", "data", "name");

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "anthropic-claude", "google-gemini", "google-vertex-ai", "azure-openai",
            "groq", "cohere", "mistral", "perplexity", "moonshot-kimi", "xai-grok",
            "together-ai", "aws-bedrock", "vercel-ai-gateway", "hugging-face",
            "openrouter", "fireworks", "nvidia-nim", "minimax", "qwen-cloud",
            "ollama", "lm-studio", "deepseek"
    })
    void llmPortsDeclareInputFilesResultAndOutputFiles(String key) throws IOException {
        Path path = NODES.resolve(key).resolve("node_definition.json");
        Map<String, Object> def = Json.asMap(Json.parse(Files.readString(path)));
        Map<String, Object> input = port(def, "llm_input");
        Map<String, Object> output = port(def, "llm_output");

        Map<String, Object> inputFiles = field(schema(input), "inputFiles");
        assertEquals("file", inputFiles.get("type"));
        assertFileChildren(inputFiles);

        Map<String, Object> result = field(schema(output), "result");
        assertNotNull(result.get("type"));

        Map<String, Object> outputFiles = field(schema(output), "outputFiles");
        assertEquals("file", outputFiles.get("type"));
        assertFileChildren(outputFiles);
    }

    private static Map<String, Object> port(Map<String, Object> def, String name) {
        for (Object raw : (List<?>) def.get("ports")) {
            Map<String, Object> p = Json.asMap(raw);
            if (name.equals(p.get("name"))) {
                return p;
            }
        }
        throw new AssertionError("missing port " + name);
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> schema(Map<String, Object> port) {
        return (List<Map<String, Object>>) (List<?>) port.get("schema");
    }

    private static Map<String, Object> field(List<Map<String, Object>> schema, String shortCode) {
        for (Map<String, Object> item : schema) {
            if (shortCode.equals(item.get("shortCode"))) {
                return item;
            }
        }
        throw new AssertionError("missing schema field " + shortCode);
    }

    private static void assertFileChildren(Map<String, Object> fileField) {
        List<?> children = (List<?>) fileField.get("children");
        assertEquals(5, children.size(), fileField.get("shortCode") + " children");
        for (int i = 0; i < FILE_CHILDREN.size(); i++) {
            Map<String, Object> child = Json.asMap(children.get(i));
            assertEquals(FILE_CHILDREN.get(i), child.get("shortCode"));
            assertTrue(child.get("type") instanceof String);
        }
    }
}
