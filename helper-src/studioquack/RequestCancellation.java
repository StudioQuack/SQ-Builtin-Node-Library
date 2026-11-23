package studioquack;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Per-request cancellation state for a stateful invoke (not a process-global flag).
 *
 * <p>The dispatcher registers one of these per {@code requestId}. {@code POST
 * .../cancel} marks it; the handler thread is interrupted and {@code onCancel}
 * callbacks run once.
 */
public final class RequestCancellation {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final List<Runnable> callbacks = new CopyOnWriteArrayList<>();
    private volatile Thread handlerThread;

    /** Bind the thread running this request's handler (so cancel can interrupt it). */
    public void attachHandlerThread(Thread t) {
        this.handlerThread = t;
    }

    /** {@code true} after {@link #markCancelled()} or an immediate onCancel race. */
    public boolean isCancelled() {
        return cancelled.get();
    }

    /**
     * Register a callback that runs once when this request is cancelled.
     * Already-cancelled → run {@code r} immediately. {@code null} is a no-op.
     */
    public void onCancel(Runnable r) {
        if (r == null) {
            return;
        }
        if (cancelled.get()) {
            runQuiet(r);
            return;
        }
        callbacks.add(r);
        // Lost the race: cancel landed between the check and the add.
        if (cancelled.get()) {
            if (callbacks.remove(r)) {
                runQuiet(r);
            }
        }
    }

    /**
     * Mark cancelled, interrupt the handler thread, run callbacks once.
     * Idempotent. Never throws.
     */
    public void markCancelled() {
        if (!cancelled.compareAndSet(false, true)) {
            return;
        }
        Thread t = handlerThread;
        if (t != null) {
            t.interrupt();
        }
        for (Runnable r : callbacks) {
            runQuiet(r);
        }
        callbacks.clear();
    }

    private static void runQuiet(Runnable r) {
        try {
            r.run();
        } catch (Exception ignored) {
            // cancellation callbacks must not fail the cancel route
        }
    }
}
