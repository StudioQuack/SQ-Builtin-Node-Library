package studioquack;

/**
 * One inbound NDJSON {@code frame} on {@code POST /invoke/stream}.
 *
 * <p>Never log {@code delta} — it may carry prompts, tokens, or PHI. Caps (256 KiB delta,
 * 1024 queued frames) are enforced by {@link StreamInvocation}, not by this type.
 *
 * @param requestId session id from the {@code open} line; empty when omitted
 * @param port      target INPUT port value; empty when omitted
 * @param channel   {@code thinking}, {@code answer}, or a producer-defined name
 * @param delta     text fragment; never {@code null} (missing becomes {@code ""})
 * @param seq       producer sequence; {@code 0} when omitted / not a number
 */
public record Frame(String requestId, String port, String channel, String delta, int seq) {

    public Frame {
        requestId = requestId == null ? "" : requestId;
        port = port == null ? "" : port;
        channel = channel == null ? "" : channel;
        delta = delta == null ? "" : delta;
    }
}
