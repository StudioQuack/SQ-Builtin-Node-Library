package studioquack;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Live per-request cancellation records keyed by {@code requestId}.
 *
 * <p>Used by {@code StudioQuackDispatch} on {@code POST $SQ_APP_PATH/cancel}
 * and by {@link StudioQuackHelper#isCancelled()} / {@code onCancel}.
 * Blank ids are never stored (a detached instance is returned instead).
 */
public final class CancellationRegistry {

    private static final ConcurrentHashMap<String, RequestCancellation> LIVE = new ConcurrentHashMap<>();

    private CancellationRegistry() {
        // utility
    }

    /**
     * Register (or reuse) the record for {@code requestId}.
     * Blank/null → a detached instance that is never looked up by {@link #cancel}.
     */
    public static RequestCancellation register(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return new RequestCancellation();
        }
        return LIVE.computeIfAbsent(requestId, k -> new RequestCancellation());
    }

    /**
     * Mark {@code requestId} cancelled.
     *
     * @return {@code true} when a live request was found and marked
     */
    public static boolean cancel(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return false;
        }
        RequestCancellation c = LIVE.get(requestId);
        if (c == null) {
            return false;
        }
        c.markCancelled();
        return true;
    }

    /** Drop the record when the request finishes (success or fail). */
    public static void unregister(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return;
        }
        LIVE.remove(requestId);
    }

    /** Live record or {@code null}. */
    public static RequestCancellation get(String requestId) {
        if (requestId == null || requestId.isBlank()) {
            return null;
        }
        return LIVE.get(requestId);
    }
}
