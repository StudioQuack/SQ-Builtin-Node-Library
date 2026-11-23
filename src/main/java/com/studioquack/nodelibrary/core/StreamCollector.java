package com.studioquack.nodelibrary.core;

import java.util.List;
import java.util.Map;

/**
 * Accumulates one provider stream into v2 {@code (channel, delta)} pieces plus a
 * final assistant payload. Request-scoped — never stored statically.
 */
public interface StreamCollector {

    /** One live delta. {@code channel} is {@code thinking} or {@code answer}. */
    record Piece(String channel, String delta) {
    }

    /**
     * Ingest one parsed payload (SSE {@code data:} JSON, one NDJSON line, or a
     * full non-stream envelope). {@code event} is the SSE {@code event:} name
     * (empty for openai-sse / ndjson). Malformed input is skipped.
     *
     * @return pieces emitted from this chunk, in order (may be empty)
     */
    List<Piece> ingest(String event, Object data);

    /** Assembled v2 assistant payload ({@link LlmAssistant#makeResponse} shape). */
    Map<String, Object> toAssistant();
}
