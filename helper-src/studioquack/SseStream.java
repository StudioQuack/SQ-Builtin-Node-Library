package studioquack;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Blocking iterator over helper-channel SSE frames. Also {@link AutoCloseable}:
 * {@link #close()} destroys the backing {@code curl} process.
 *
 * <p>Use with try-with-resources. One iterator per stream (do not share across
 * requests — each helper call owns its own {@code SseStream}). Helper calls never
 * throw: a down channel is an already-failed empty stream ({@link #transportFailed()}
 * is true). Never log the helper bearer or request JSON.
 *
 * <pre>{@code
 * try (SseStream stream = helper.executeStream("log", Map.of("message", "hello"))) {
 *     for (SseFrame frame : stream) {
 *         if ("done".equals(frame.event)) {
 *             break;
 *         }
 *     }
 *     if (stream.transportFailed()) {
 *         helper.log("stream down: " + stream.failureHint(), "warn");
 *     }
 * }
 * }</pre>
 */
public final class SseStream implements Iterable<SseFrame>, AutoCloseable {

    /** Identity poison-pill; never published to callers (empty event+data is not dispatched). */
    private static final SseFrame POISON = new SseFrame("", null, "");

    /** Back-pressure bound; the daemon's own queue is 1024. */
    private static final int QUEUE_CAP = 256;

    /** Cap on a sniffed JSON error body so a hostile blob cannot pin sandbox RAM. */
    private static final int JSON_SNIFF_CAP = 64 * 1024;

    // Streams stay open without an idle timeout; the daemon closes the stream on cancellation.

    private final BlockingQueue<SseFrame> frames = new ArrayBlockingQueue<>(QUEUE_CAP);
    private final CountDownLatch classified = new CountDownLatch(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicBoolean ended = new AtomicBoolean(false);
    private final AtomicBoolean iteratorIssued = new AtomicBoolean(false);

    private final Process process;
    private final Thread readerThread;

    private volatile boolean transportFailed;
    private volatile String failureHint = "";
    private int framesParsed;

    /** Already-failed, closed stream (missing token/socket, curl did not start, blank port). */
    static SseStream failed(String hint) {
        return new SseStream(hint);
    }

    /** Take ownership of a live {@code curl} process and start the line reader immediately. */
    static SseStream fromProcess(Process process) {
        if (process == null) {
            return failed("curl start failed: null process");
        }
        return new SseStream(process);
    }

    private SseStream(String hint) {
        this.process = null;
        this.readerThread = null;
        this.transportFailed = true;
        this.failureHint = hint == null ? "" : hint;
        signalEnd();
        classified.countDown();
    }

    private SseStream(Process process) {
        this.process = process;
        Thread t = new Thread(this::readLoop, "sq-helper-sse");
        t.setDaemon(true);
        this.readerThread = t;
        t.start();
    }

    /**
     * {@code true} when the helper channel could not produce SSE: missing socket/token,
     * curl failed to start, the process exited non-zero with zero frames, or the body was
     * a JSON error object rather than an event stream.
     *
     * <p>Waits until the stream has been classified (immediately for a pre-failed stream;
     * after the first SSE field or EOF otherwise). Never includes the bearer.
     */
    public boolean transportFailed() {
        awaitClassified();
        return transportFailed;
    }

    /**
     * Short class/status when {@link #transportFailed()} is true (e.g. {@code missing token},
     * {@code curl exit 7}, {@code json error: unauthorized}). Empty when the stream did not
     * fail. Never the bearer, never a full response body.
     */
    public String failureHint() {
        awaitClassified();
        return failureHint == null ? "" : failureHint;
    }

    /**
     * One-shot iterator. Blocks until the next frame or end-of-stream. Does not throw
     * on transport failure (the iterator is simply empty).
     */
    @Override
    public Iterator<SseFrame> iterator() {
        if (!iteratorIssued.compareAndSet(false, true)) {
            return Collections.emptyIterator();
        }
        return new Itr();
    }

    /**
     * Destroy {@code curl}, interrupt the reader, unblock iterators. Quiet — no argv,
     * no bearer, no body on stderr.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        if (process != null) {
            try {
                process.destroyForcibly();
            } catch (Exception ignored) {
                // best-effort
            }
            try {
                process.getInputStream().close();
            } catch (Exception ignored) {
                // unblocks readLine
            }
        }
        if (readerThread != null) {
            readerThread.interrupt();
        }
        signalEnd();
        classified.countDown();
        if (readerThread != null) {
            try {
                readerThread.join(2_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /* ------------------------------------------------------------------ */
    /* Reader                                                             */
    /* ------------------------------------------------------------------ */

    private void readLoop() {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            parseSse(reader);
        } catch (Exception e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (!closed.get() && framesParsed == 0 && !transportFailed) {
                transportFailed = true;
                if (failureHint.isEmpty()) {
                    failureHint = "curl read failed: " + e.getClass().getSimpleName();
                }
            }
        } finally {
            int code = 0;
            try {
                if (process != null) {
                    code = process.waitFor();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                code = -1;
            }
            // User abort (close) is not a transport failure.
            if (!closed.get() && framesParsed == 0 && code != 0) {
                transportFailed = true;
                if (failureHint.isEmpty()) {
                    failureHint = "curl exit " + code;
                }
            }
            classified.countDown();
            signalEnd();
        }
    }

    /**
     * Incremental SSE: comments ({@code :…}) ignored; {@code event:} sets the name;
     * {@code data:} appends (joined with {@code \n}); a blank line dispatches; leftover
     * dispatched on EOF. {@code event: done} is yielded. A leading JSON object with an
     * {@code error} key is a failed transport, not SSE.
     */
    private void parseSse(BufferedReader reader) throws Exception {
        String currentEvent = "";
        StringBuilder currentData = new StringBuilder();
        boolean sseMode = false;
        StringBuilder jsonBuf = null;
        String line;
        while ((line = reader.readLine()) != null) {
            if (closed.get()) {
                break;
            }
            if (!sseMode && jsonBuf != null) {
                appendJsonSniff(jsonBuf, line);
                continue;
            }
            if (!sseMode) {
                if (line.startsWith(":")) {
                    sseMode = true;
                    classified.countDown();
                    continue;
                }
                if (isSseField(line)) {
                    sseMode = true;
                    classified.countDown();
                    // fall through and parse this line as SSE
                } else if (!line.isEmpty() && startsJsonObject(line)) {
                    jsonBuf = new StringBuilder();
                    appendJsonSniff(jsonBuf, line);
                    continue;
                } else {
                    continue;
                }
            }
            if (line.startsWith(":")) {
                continue;
            }
            if (line.isEmpty()) {
                dispatch(currentEvent, currentData.toString());
                currentEvent = "";
                currentData.setLength(0);
                continue;
            }
            if (line.startsWith("event:")) {
                currentEvent = line.substring("event:".length()).trim();
                continue;
            }
            if (line.startsWith("data:")) {
                String payload = line.substring("data:".length());
                // WHATWG SSE: drop one leading U+0020 after the colon.
                if (!payload.isEmpty() && payload.charAt(0) == ' ') {
                    payload = payload.substring(1);
                }
                if (currentData.length() > 0) {
                    currentData.append('\n');
                }
                currentData.append(payload);
            }
            // id: / retry: / unknown — ignore
        }
        if (sseMode) {
            dispatch(currentEvent, currentData.toString());
        }
        if (jsonBuf != null && framesParsed == 0) {
            classifyJsonError(jsonBuf.toString());
        }
    }

    private void dispatch(String event, String raw) throws InterruptedException {
        String ev = event == null ? "" : event;
        String data = raw == null ? "" : raw;
        if (ev.isEmpty() && data.isEmpty()) {
            return;
        }
        if (closed.get()) {
            return;
        }
        framesParsed++;
        frames.put(SseFrame.fromEvent(ev, data));
    }

    private void classifyJsonError(String raw) {
        String t = raw == null ? "" : raw.trim();
        if (t.isEmpty() || t.charAt(0) != '{') {
            return;
        }
        try {
            Object parsed = Json.parse(t);
            // {error: …} (daemon, OpenAI) or {object: "error", message, type, code} (SGLang, vLLM).
            if (parsed instanceof Map<?, ?> map
                    && (map.containsKey("error") || "error".equals(String.valueOf(map.get("object"))))) {
                transportFailed = true;
                if (failureHint.isEmpty()) {
                    failureHint = jsonErrorHint(map.containsKey("error") ? map.get("error") : map.get("type"));
                }
            }
        } catch (Exception ignored) {
            // not a JSON object — leave transportFailed to the exit-code path
        }
    }

    /** Short status only; never a sentence that might embed payload/secrets. */
    private static String jsonErrorHint(Object error) {
        if (error instanceof String s) {
            String t = s.trim();
            if (!t.isEmpty() && t.length() <= 64 && t.indexOf(' ') < 0 && t.indexOf('\n') < 0) {
                return "json error: " + t;
            }
        }
        return "json error";
    }

    private static boolean isSseField(String line) {
        return line.startsWith("event:")
                || line.startsWith("data:")
                || line.startsWith("id:")
                || line.startsWith("retry:");
    }

    private static boolean startsJsonObject(String line) {
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == ' ' || c == '\t') {
                i++;
            } else {
                return c == '{';
            }
        }
        return false;
    }

    private static void appendJsonSniff(StringBuilder buf, String line) {
        if (buf.length() >= JSON_SNIFF_CAP) {
            return;
        }
        if (buf.length() > 0) {
            buf.append('\n');
        }
        int room = JSON_SNIFF_CAP - buf.length();
        if (line.length() <= room) {
            buf.append(line);
        } else {
            buf.append(line, 0, room);
        }
    }

    private void signalEnd() {
        if (!ended.compareAndSet(false, true)) {
            return;
        }
        // Always land the pill, even if the frame queue is full (close / EOF).
        while (!frames.offer(POISON)) {
            frames.poll();
        }
    }

    private void awaitClassified() {
        try {
            classified.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /* ------------------------------------------------------------------ */
    /* Iterator                                                           */
    /* ------------------------------------------------------------------ */

    private final class Itr implements Iterator<SseFrame> {
        private SseFrame next;
        private boolean finished;

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            if (next != null) {
                return true;
            }
            try {
                SseFrame f = frames.take();
                if (f == POISON) {
                    finished = true;
                    return false;
                }
                next = f;
                return true;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                finished = true;
                return false;
            }
        }

        @Override
        public SseFrame next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            SseFrame f = next;
            next = null;
            return f;
        }
    }
}
