package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.Json;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration gate for every packaged api_spec.json stream block, including
 * specs a sibling agent adds. Unknown formats or mistyped fields fail the build.
 */
class OfficialApiStreamSpecTest {

    private static final Path NODES = Path.of("nodes");

    @Test
    void everyStreamBlockIsKnownAndWellTyped() throws IOException {
        int seen = 0;
        try (Stream<Path> dirs = Files.list(NODES)) {
            List<Path> specs = dirs.filter(Files::isDirectory)
                    .map(p -> p.resolve("api_spec.json"))
                    .filter(Files::isRegularFile)
                    .sorted()
                    .toList();
            assertFalse(specs.isEmpty(), "expected packaged api_spec.json files");
            for (Path specPath : specs) {
                Map<String, Object> spec = Json.asMap(Json.parse(Files.readString(specPath)));
                assertStringMap(specPath + ".extraHeaders", spec.get("extraHeaders"));
                Object ops = spec.get("operations");
                if (!(ops instanceof Map<?, ?> om)) {
                    continue;
                }
                for (var e : om.entrySet()) {
                    if (!(e.getValue() instanceof Map<?, ?> op)) {
                        continue;
                    }
                    assertStringMap(specPath + "#" + e.getKey() + ".extraHeaders", op.get("extraHeaders"));
                    if (!op.containsKey("stream")) {
                        continue;
                    }
                    seen++;
                    Object stream = op.get("stream");
                    if (!(stream instanceof Map<?, ?> sm)) {
                        fail(specPath + " op " + e.getKey() + ": stream must be an object");
                        continue;
                    }
                    assertStreamBlock(specPath + "#" + e.getKey(), sm);
                }
            }
        }
        assertTrue(seen >= 6, "expected at least the six reference stream blocks, saw " + seen);
    }

    private static void assertStreamBlock(String where, Map<?, ?> sm) {
        String format = str(sm.get("format")).toLowerCase();
        assertTrue(ProviderStream.knownFormat(format),
                where + ": unknown stream.format '" + format + "'; known=" + ProviderStream.FORMATS);

        if (sm.containsKey("requestFlag")) {
            assertFalse(str(sm.get("requestFlag")).isBlank(), where + ": requestFlag blank");
        }
        if (sm.containsKey("requestValue") && sm.get("requestValue") == null) {
            fail(where + ": requestValue must not be JSON null");
        }
        if (sm.containsKey("path") && !(sm.get("path") instanceof String)) {
            fail(where + ": path must be a string");
        }
        if (sm.containsKey("port")) {
            assertFalse(str(sm.get("port")).isBlank(), where + ": port blank");
        }
        assertStringMap(where + ".query", sm.get("query"));
        assertStringMap(where + ".extraHeaders", sm.get("extraHeaders"));
    }

    private static void assertStringMap(String where, Object raw) {
        if (raw == null) {
            return;
        }
        if (!(raw instanceof Map<?, ?> m)) {
            fail(where + " must be an object");
            return;
        }
        for (var e : m.entrySet()) {
            if (e.getKey() == null || str(e.getKey()).isBlank()) {
                fail(where + ": blank key");
            }
            if (!(e.getValue() instanceof String) && !(e.getValue() instanceof Number)
                    && !(e.getValue() instanceof Boolean)) {
                fail(where + "." + e.getKey() + " must be a string/number/boolean");
            }
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}
