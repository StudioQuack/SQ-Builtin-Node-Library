package com.studioquack.nodelibrary.nodes.openai;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** OpenAiMedia error-body helper — no network. */
class OpenAiMediaTest {

    @Test
    void providerErrorMessageUsesOpenaiErrorMessage() {
        String parsed = OpenAiMedia.providerErrorMessage(400,
                "{\"error\":{\"message\":\"invalid content part type\"}}");
        assertEquals("invalid content part type", parsed);
        assertEquals("HTTP 400", OpenAiMedia.providerErrorMessage(400, ""));
        assertEquals("HTTP 422", OpenAiMedia.providerErrorMessage(422, "not-json"));
        assertTrue(OpenAiMedia.providerErrorMessage(401, "{\"error\":{\"message\":\"sk-secretkeyvalue\"}}")
                .contains("sk-[redacted]"));
    }
}
