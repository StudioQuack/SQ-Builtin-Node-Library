package com.studioquack.nodelibrary.core;

import studioquack.StreamEmitter;
import studioquack.StreamInvocation;
import studioquack.StudioQuackHelper;

import java.util.Map;

/**
 * In-process StudioQuack node hosted by the SQ Builtin Node Library image.
 *
 * <p>Implementations run on the stateful listener thread pool. Reply is always a
 * JSON object keyed by the node's OUTPUT port names. Never log
 * {@code helperToken}.
 *
 * <p>Unary {@link #handle} is the default path ({@code POST /invoke}). {@code POST
 * /invoke/stream} calls {@link #handleStream}: one-shot nodes keep the default
 * (buffer frames, then {@link #handle}) and need no code changes. Stream-through
 * nodes override {@link #supportsStreamInput()} to {@code true} and
 * {@link #handleStream}.
 */
public interface NodeHandler {

    /** Resolved template id this handler owns (definition {@code _id} or env override). */
    String templateId();

    /**
     * Handle one invoke body. {@code helper} is the per-request helper channel;
     * never log {@code helperToken} from {@code request}.
     *
     * @param request daemon POST body (may be empty; treat null as invalid)
     * @param helper  per-request helper (may be unused by a given node)
     * @return output-port map; {@code null} becomes {@code {}} in dispatch
     */
    Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper);

    /**
     * {@code true} when this node consumes inbound frames live (overrides
     * {@link #handleStream}). Default {@code false}: the dispatcher still accepts
     * {@code /invoke/stream} but the default {@link #handleStream} waits for close
     * and delegates to {@link #handle}.
     */
    default boolean supportsStreamInput() {
        return false;
    }

    /**
     * Bidirectional stream invocation. Default: wait for input close, assemble
     * per-port text, call {@link #handle}, {@link StreamEmitter#complete} the result.
     *
     * <p>Override together with {@link #supportsStreamInput()}{@code = true} to
     * {@link StreamInvocation#pollFrame poll} frames while emitting partials.
     * Never log frame deltas. Thrown exceptions become {@code event: error}
     * (simple name + message, no stack).
     */
    default void handleStream(StreamInvocation in, StreamEmitter out, StudioQuackHelper helper) {
        if (in == null) {
            if (out != null) {
                out.error("missing stream session");
            }
            return;
        }
        in.awaitInputComplete();
        if (in.isFailed()) {
            out.error(in.failureMessage());
            return;
        }
        try {
            Map<String, Object> result = handle(in.assembledInvokeBody(), helper);
            out.complete(result);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            String message = e.getMessage() == null ? "" : e.getMessage();
            String shaped = e.getClass().getSimpleName() + ": " + message;
            out.error(shaped.length() <= 500 ? shaped : shaped.substring(0, 500));
        }
    }
}
