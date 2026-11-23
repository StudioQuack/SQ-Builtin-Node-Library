package studioquack;

/**
 * One Server-Sent Event from a helper-channel stream ({@link SseStream}).
 *
 * <p>{@code event} is the SSE event name ({@code partial}, {@code complete}, {@code error},
 * {@code done}, a provider name, or {@code ""} when the frame omitted {@code event:}).
 * {@code rawData} is the concatenated {@code data:} payload (multi-line values joined with
 * {@code \n}). {@code data} is {@link Json#parse(String)} of that payload when it is valid
 * JSON, otherwise the raw string (so OpenAI {@code data: [DONE]} stays a string).
 *
 * <p>Never log {@code data}/{@code rawData} — they may carry prompts, tokens, or PHI.
 *
 * <pre>{@code
 * for (SseFrame frame : helper.executeStream("log", payload)) {
 *     if ("partial".equals(frame.event)) {
 *         // frame.data is the parsed chunk
 *     }
 * }
 * }</pre>
 */
public final class SseFrame {

    /** SSE {@code event:} name; empty string when the frame omitted the field. */
    public final String event;

    /** Parsed JSON value, or the raw string when {@link Json#parse} cannot read it. */
    public final Object data;

    /** Concatenated {@code data:} payload, never {@code null}. */
    public final String rawData;

    /**
     * @param event   SSE event name; {@code null} becomes {@code ""}
     * @param data    parsed payload (may be {@code null} when the data field is blank)
     * @param rawData concatenated {@code data:} bytes; {@code null} becomes {@code ""}
     */
    public SseFrame(String event, Object data, String rawData) {
        this.event = event == null ? "" : event;
        this.data = data;
        this.rawData = rawData == null ? "" : rawData;
    }

    /**
     * Build a frame, parsing {@code rawData} with {@link Json#parse} when possible.
     */
    static SseFrame fromEvent(String event, String rawData) {
        String raw = rawData == null ? "" : rawData;
        Object parsed;
        try {
            parsed = Json.parse(raw);
            if (parsed == null && !raw.isBlank()) {
                parsed = raw;
            }
        } catch (Exception ignored) {
            parsed = raw;
        }
        return new SseFrame(event, parsed, raw);
    }
}
