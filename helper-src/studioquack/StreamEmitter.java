package studioquack;

import java.util.Map;

/**
 * Outbound side of {@code POST /invoke/stream}. Implementations flush SSE incrementally
 * and never throw into the node.
 *
 * <p>On success the dispatcher writes {@code event: complete} then {@code event: done}.
 * On failure, {@code event: error} then {@code event: done}. {@code done} is always last.
 * First terminal call ({@link #complete} or {@link #error}) wins; later terminals are
 * no-ops.
 *
 * <p>Never log {@code delta} or {@code result} — they may carry PHI.
 */
public interface StreamEmitter {

    /**
     * Live fragment ({@code event: partial}, {@code data: {"channel","delta"}}).
     * Blank {@code channel} or {@code null} {@code delta} is a no-op. Empty-string
     * delta is sent.
     */
    void emitPartial(String channel, String delta);

    /**
     * Assembled node result ({@code event: complete}). {@code null} becomes {@code {}}.
     */
    void complete(Map<String, Object> result);

    /**
     * Failure ({@code event: error}, {@code data: {"error":"<message>"}}). Blank
     * {@code message} becomes {@code "error"}. Callers must not pass stack traces.
     */
    void error(String message);
}
