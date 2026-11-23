package studioquack;

import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One {@code POST /invoke/stream} session: inbound frame queue plus assembled-per-port
 * text. The HTTP reader offers frames; the node polls them (or ignores them and reads
 * {@link #assembledInvokeBody()} after close).
 *
 * <p>Bounds (DoS): 1024 queued frames (drop-oldest, counter only — never a log line),
 * 256 KiB per delta, 8 MiB assembled text per port. Never log frame/delta content.
 */
public final class StreamInvocation {

    /** Bounded inbound queue. Excess offers drop the oldest frame and increment {@link #droppedFrameCount()}. */
    public static final int MAX_QUEUED_FRAMES = 1024;

    /** Truncate point for each frame {@code delta} (matches daemon emit-partial). */
    public static final int MAX_DELTA_CHARS = 256 * 1024;

    /** Assembled text cap per INPUT port (unary body cap). Further appends are skipped. */
    public static final int MAX_ASSEMBLED_CHARS_PER_PORT = StudioQuackDispatch.MAX_BODY_BYTES;

    private final String requestId;
    private final String templateId;
    private final Map<String, Object> openInput;
    private final Map<String, Object> context;
    private final ArrayBlockingQueue<Frame> frames = new ArrayBlockingQueue<>(MAX_QUEUED_FRAMES);
    private final ConcurrentHashMap<String, StringBuilder> assembled = new ConcurrentHashMap<>();
    private final Object queueLock = new Object();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicBoolean inputComplete = new AtomicBoolean(false);
    private final AtomicBoolean failed = new AtomicBoolean(false);
    private volatile String failureMessage = "";

    StreamInvocation(String requestId, String templateId,
                     Map<String, Object> openInput, Map<String, Object> context) {
        this.requestId = requestId == null ? "" : requestId;
        this.templateId = templateId == null ? "" : templateId;
        this.openInput = copyMap(openInput);
        this.context = copyMap(context);
        // Seed assembled text from string-valued open.input so unary nodes see prefix+deltas.
        for (Map.Entry<String, Object> e : this.openInput.entrySet()) {
            if (e.getValue() instanceof String s) {
                StringBuilder sb = new StringBuilder();
                appendCapped(sb, s);
                this.assembled.put(e.getKey(), sb);
            }
        }
    }

    public String requestId() {
        return requestId;
    }

    public String templateId() {
        return templateId;
    }

    /** {@code open.input} port map as received (never {@code null}). */
    public Map<String, Object> openInput() {
        return copyMap(openInput);
    }

    /** {@code open.context} execution-context map (never {@code null}). */
    public Map<String, Object> context() {
        return copyMap(context);
    }

    /**
     * Wait up to {@code timeout} for the next inbound frame. Empty on timeout, after
     * {@link #isInputComplete()} with a drained queue, or after {@link #isFailed()}.
     */
    public Optional<Frame> pollFrame(Duration timeout) {
        if (failed.get()) {
            return Optional.empty();
        }
        long ms = timeout == null ? 0L : Math.max(0L, timeout.toMillis());
        try {
            Frame f = frames.poll(ms, TimeUnit.MILLISECONDS);
            return Optional.ofNullable(f);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    /**
     * Iterate frames until close or failure. Blocking. Do not share the iterator
     * across threads.
     */
    public Iterable<Frame> frames() {
        return () -> new UntilCloseIterator();
    }

    public boolean isInputComplete() {
        return inputComplete.get();
    }

    public boolean isFailed() {
        return failed.get();
    }

    /** Safe, short reason when {@link #isFailed()}; never a stack, never a delta. */
    public String failureMessage() {
        String m = failureMessage;
        return m == null ? "" : m;
    }

    /** Drop-oldest counter. Incremented only; never logged. */
    public long droppedFrameCount() {
        return dropped.get();
    }

    /**
     * Port map after concatenating frame deltas onto {@code open.input}. String ports
     * become the assembled text; non-string ports that never received a frame stay as
     * opened. Frame-only ports appear as assembled text.
     */
    public Map<String, Object> assembledInput() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : openInput.entrySet()) {
            StringBuilder sb = assembled.get(e.getKey());
            if (sb != null) {
                out.put(e.getKey(), sb.toString());
            } else {
                out.put(e.getKey(), e.getValue());
            }
        }
        for (Map.Entry<String, StringBuilder> e : assembled.entrySet()) {
            if (!out.containsKey(e.getKey())) {
                out.put(e.getKey(), e.getValue().toString());
            }
        }
        return out;
    }

    /**
     * Unary {@code /invoke}-shaped body so existing {@code NodeHandler#handle} implementations
     * see {@code inputVariables}, {@code executionContext}, and {@code templateId}.
     */
    public Map<String, Object> assembledInvokeBody() {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("requestId", requestId);
        req.put("templateId", templateId);
        req.put("executionContext", copyMap(context));
        Map<String, Object> ports = assembledInput();
        req.put("input", ports);
        req.put("inputVariables", ports);
        String token = Json.str(context.get("helperToken"));
        if (!token.isBlank()) {
            req.put("helperToken", token);
        }
        copyIfPresent(context, req, "workflowExecutionId");
        copyIfPresent(context, req, "currentNodeId");
        copyIfPresent(context, req, "envId");
        copyIfPresent(context, req, "bespokeDir");
        return req;
    }

    /**
     * Block until {@code close}/EOF or failure. Frames are discarded here — they were
     * already folded into {@link #assembledInput()} on offer.
     */
    public void awaitInputComplete() {
        while (!failed.get() && !inputComplete.get()) {
            pollFrame(Duration.ofMillis(200));
        }
        // Drain anything that raced in after the last poll.
        while (frames.poll() != null) {
            // assembled already updated on offer
        }
    }

    void offerFrame(Frame frame) {
        if (frame == null || inputComplete.get() || failed.get()) {
            return;
        }
        Frame capped = capDelta(frame);
        appendAssembled(capped.port(), capped.delta());
        synchronized (queueLock) {
            if (frames.remainingCapacity() == 0) {
                Frame droppedFrame = frames.poll();
                if (droppedFrame != null) {
                    dropped.incrementAndGet();
                }
            }
            frames.offer(capped);
        }
    }

    void markInputComplete() {
        inputComplete.set(true);
    }

    void fail(String message) {
        if (!failed.compareAndSet(false, true)) {
            return;
        }
        failureMessage = capReason(message);
        inputComplete.set(true);
    }

    private static Frame capDelta(Frame frame) {
        String d = frame.delta();
        if (d.length() <= MAX_DELTA_CHARS) {
            return frame;
        }
        return new Frame(frame.requestId(), frame.port(), frame.channel(),
                d.substring(0, MAX_DELTA_CHARS), frame.seq());
    }

    private void appendAssembled(String port, String delta) {
        String key = port == null ? "" : port;
        String add = delta == null ? "" : delta;
        if (add.isEmpty()) {
            assembled.computeIfAbsent(key, k -> new StringBuilder());
            return;
        }
        assembled.compute(key, (k, sb) -> {
            StringBuilder buf = sb == null ? new StringBuilder() : sb;
            appendCapped(buf, add);
            return buf;
        });
    }

    private static void appendCapped(StringBuilder sb, String add) {
        int room = MAX_ASSEMBLED_CHARS_PER_PORT - sb.length();
        if (room <= 0 || add == null || add.isEmpty()) {
            return;
        }
        if (add.length() <= room) {
            sb.append(add);
        } else {
            sb.append(add, 0, room);
        }
    }

    private static void copyIfPresent(Map<String, Object> from, Map<String, Object> to, String key) {
        Object v = from.get(key);
        if (v != null) {
            to.put(key, v);
        }
    }

    private static Map<String, Object> copyMap(Map<String, Object> src) {
        if (src == null || src.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return new LinkedHashMap<>(src);
    }

    private static String capReason(String message) {
        if (message == null || message.isBlank()) {
            return "error";
        }
        String t = message.trim();
        return t.length() <= 500 ? t : t.substring(0, 500);
    }

    private final class UntilCloseIterator implements Iterator<Frame> {
        private Frame next;
        private boolean finished;

        @Override
        public boolean hasNext() {
            if (finished) {
                return false;
            }
            if (next != null) {
                return true;
            }
            while (true) {
                Optional<Frame> f = pollFrame(Duration.ofMillis(200));
                if (f.isPresent()) {
                    next = f.get();
                    return true;
                }
                if (failed.get() || inputComplete.get()) {
                    Frame leftover = frames.poll();
                    if (leftover != null) {
                        next = leftover;
                        return true;
                    }
                    finished = true;
                    return false;
                }
            }
        }

        @Override
        public Frame next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            Frame f = next;
            next = null;
            return f;
        }
    }
}
