package studioquack;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * {@code POST /invoke/stream}: NDJSON frames in, SSE events out. Incremental — the
 * request body is never buffered whole. Same bind as {@code /invoke} (container-local
 * by deployment). Never logs frame/delta content or {@code helperToken}.
 */
final class InvokeStreamSupport {

    /** Idle gap between inbound lines; overall session budget is {@link #TOTAL_BUDGET_MS}. */
    static final long IDLE_TIMEOUT_MS = TimeUnit.MINUTES.toMillis(5);

    static final long TOTAL_BUDGET_MS = TimeUnit.MINUTES.toMillis(30);

    /** One NDJSON line (and thus one JSON object) may not exceed the unary body cap. */
    static final int MAX_LINE_CHARS = StudioQuackDispatch.MAX_BODY_BYTES;

    private static final int OUT_QUEUE_CAP = 1024;
    private static final String SSE_CONTENT_TYPE = "text/event-stream";

    /**
     * Global cap on concurrent stream sessions. Unlike unary {@code /invoke}, a stream session
     * holds threads and a socket for up to {@link #TOTAL_BUDGET_MS}, so a bridge-reachable actor
     * could otherwise pin unbounded resources by opening many sessions. 32 concurrent sessions
     * is far above legitimate fan-out (one per wired stream-through edge per run).
     */
    private static final int MAX_SESSIONS = 32;
    private static final java.util.concurrent.Semaphore SESSION_SLOTS =
            new java.util.concurrent.Semaphore(MAX_SESSIONS);

    /**
     * Shared daemon executor for reader/worker tasks — bounded in practice by
     * {@link #SESSION_SLOTS} (≤ 2 tasks per session). A per-request pool would let each
     * session create threads outside any global limit.
     */
    private static final ExecutorService STREAM_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "sq-invoke-stream");
        t.setDaemon(true);
        return t;
    });

    private InvokeStreamSupport() {
    }

    static void handle(HttpExchange ex, StudioQuackDispatch.StreamHandlerLookup lookup) {
        if (ex == null) {
            return;
        }
        try {
            String method = ex.getRequestMethod();
            if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
                StudioQuackDispatch.sendJson(ex, 200, Map.of("status", "up"));
                return;
            }
            if (!"POST".equalsIgnoreCase(method)) {
                StudioQuackDispatch.sendJson(ex, 200, StudioQuackDispatch.dispatchFail(
                        StudioQuackDispatch.DISPATCH_BAD_REQUEST, "POST required"));
                return;
            }
            runStream(ex, lookup);
        } catch (Exception e) {
            tryWriteFatal(ex, safeError(e));
        }
    }

    /**
     * Unary {@link StudioQuackDispatch.InvokeHandler} over the stream endpoint: wait for
     * close, then {@code handle(assembled, helper)}.
     */
    static StudioQuackDispatch.StreamInvokeHandler wrapUnary(StudioQuackDispatch.InvokeHandler handler) {
        return (in, out, helper) -> {
            if (handler == null) {
                out.error("no self handler");
                return;
            }
            in.awaitInputComplete();
            if (in.isFailed()) {
                out.error(in.failureMessage());
                return;
            }
            Map<String, Object> result = handler.handle(in.assembledInvokeBody(), helper);
            String failure = StudioQuackDispatch.failureErrorText(result);
            if (failure != null) {
                System.err.println("StudioQuackDispatch: deprecated top-level \"error\" key; "
                        + "throw StudioQuackError or call helper.fail instead");
                StudioQuackDispatch.foldFail(helper, "NODE_ERROR", failure, null);
                out.complete(Map.of());
                return;
            }
            out.complete(result == null ? Map.of() : result);
        };
    }

    private static void runStream(HttpExchange ex, StudioQuackDispatch.StreamHandlerLookup lookup) {
        if (!SESSION_SLOTS.tryAcquire()) {
            // Global concurrency cap hit — refuse cheaply instead of pinning more threads.
            tryWriteFatal(ex, "too many concurrent stream sessions");
            return;
        }
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(TOTAL_BUDGET_MS);
        ArrayBlockingQueue<OutEvent> outQ = new ArrayBlockingQueue<>(OUT_QUEUE_CAP);
        AtomicBoolean terminal = new AtomicBoolean(false);
        StreamEmitter emitter = new QueueEmitter(outQ, terminal);

        Future<?> reader = null;
        Future<?> worker = null;
        SessionBox sessionBox = new SessionBox();
        try {
            if (!openSse(ex)) {
                return;
            }
            reader = STREAM_POOL.submit(() -> readNdjson(ex, sessionBox, emitter, deadlineNanos));
            StreamInvocation session = awaitOpen(sessionBox, emitter, deadlineNanos);
            if (session == null) {
                drainSse(ex, outQ, terminal, deadlineNanos);
                return;
            }
            StudioQuackDispatch.StreamInvokeHandler handler = resolve(lookup, session.templateId());
            if (handler == null) {
                emitter.error("unknown templateId: " + session.templateId());
                drainSse(ex, outQ, terminal, deadlineNanos);
                return;
            }
            StudioQuackHelper helper = helperFrom(session);
            worker = STREAM_POOL.submit(() -> runNode(handler, session, emitter, helper));
            drainSse(ex, outQ, terminal, deadlineNanos);
        } catch (Exception e) {
            emitter.error(safeError(e));
            drainSse(ex, outQ, terminal, deadlineNanos);
        } finally {
            if (reader != null) {
                reader.cancel(true);
            }
            if (worker != null) {
                worker.cancel(true);
            }
            SESSION_SLOTS.release();
            try {
                ex.close();
            } catch (Exception ignored) {
                // never throw
            }
        }
    }

    private static StudioQuackDispatch.StreamInvokeHandler resolve(
            StudioQuackDispatch.StreamHandlerLookup lookup, String templateId) {
        if (lookup == null || templateId == null || templateId.isBlank()) {
            return null;
        }
        if (!StudioQuackDispatch.validTemplateId(templateId)) {
            return null;
        }
        try {
            return lookup.lookup(templateId);
        } catch (Exception e) {
            return null;
        }
    }

    private static StudioQuackHelper helperFrom(StreamInvocation session) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("executionContext", session.context());
        body.put("helperToken", Json.str(session.context().get("helperToken")));
        body.put("requestId", session.requestId());
        body.put("templateId", session.templateId());
        return StudioQuackDispatch.helperFor(body);
    }

    private static void runNode(StudioQuackDispatch.StreamInvokeHandler handler,
                                StreamInvocation session, StreamEmitter emitter,
                                StudioQuackHelper helper) {
        try {
            handler.handle(session, emitter, helper);
            // If the node returned without a terminal, synthesise complete of empty.
            if (emitter instanceof QueueEmitter qe && !qe.terminal.get()) {
                emitter.complete(Map.of());
            }
        } catch (StudioQuackError e) {
            StudioQuackDispatch.foldFail(helper, e.errorCode(), e.getMessage(), e.data());
            emitter.complete(Map.of());
        } catch (Exception e) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("exceptionType", e.getClass().getName());
            String msg = e.getClass().getSimpleName() + ": "
                    + (e.getMessage() == null ? "" : e.getMessage());
            StudioQuackDispatch.foldFail(helper, "SANDBOX_FAILED", msg, data);
            emitter.complete(Map.of());
        } finally {
            CancellationRegistry.unregister(session == null ? "" : session.requestId());
        }
    }

    private static StreamInvocation awaitOpen(SessionBox box, StreamEmitter emitter, long deadlineNanos) {
        while (System.nanoTime() < deadlineNanos) {
            StreamInvocation s = box.session;
            if (s != null) {
                return s;
            }
            if (box.openFailed) {
                return null;
            }
            try {
                Thread.sleep(20);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.error("interrupted");
                return null;
            }
        }
        emitter.error("timed out");
        return null;
    }

    private static void readNdjson(HttpExchange ex, SessionBox box, StreamEmitter emitter,
                                   long deadlineNanos) {
        ArrayBlockingQueue<Object> lines = new ArrayBlockingQueue<>(32);
        AtomicBoolean stop = new AtomicBoolean(false);
        Thread blocker = new Thread(() -> pumpLines(ex, lines, stop), "sq-invoke-stream-body");
        blocker.setDaemon(true);
        blocker.start();
        try {
            while (System.nanoTime() < deadlineNanos) {
                long remainTotal = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                if (remainTotal <= 0) {
                    break;
                }
                Object item = lines.poll(Math.min(IDLE_TIMEOUT_MS, remainTotal), TimeUnit.MILLISECONDS);
                if (item == null) {
                    fail(box, emitter, "idle timeout");
                    return;
                }
                if (item == LineRead.TOO_LONG) {
                    fail(box, emitter, "request body too large");
                    return;
                }
                if (item == LineRead.EOF) {
                    StreamInvocation s = box.session;
                    if (s == null && !box.openFailed) {
                        fail(box, emitter, "missing open");
                    } else if (s != null) {
                        s.markInputComplete();
                    }
                    return;
                }
                String line = (String) item;
                if (line.isBlank()) {
                    continue;
                }
                if (!consumeLine(line, box, emitter)) {
                    return;
                }
            }
            fail(box, emitter, "timed out");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail(box, emitter, "interrupted");
        } catch (Exception e) {
            fail(box, emitter, safeError(e));
        } finally {
            stop.set(true);
            closeBodyQuietly(ex);
            blocker.interrupt();
        }
    }

    private static void pumpLines(HttpExchange ex, ArrayBlockingQueue<Object> lines, AtomicBoolean stop) {
        try (Reader raw = new InputStreamReader(requestBody(ex), StandardCharsets.UTF_8)) {
            LineScanner scanner = new LineScanner(raw);
            while (!stop.get()) {
                String line = scanner.readLine(MAX_LINE_CHARS);
                if (line == LineRead.TOO_LONG) {
                    offerLine(lines, LineRead.TOO_LONG);
                    return;
                }
                if (line == null) {
                    offerLine(lines, LineRead.EOF);
                    return;
                }
                offerLine(lines, line);
            }
        } catch (Exception e) {
            offerLine(lines, LineRead.EOF);
        }
    }

    private static void offerLine(ArrayBlockingQueue<Object> lines, Object item) {
        try {
            lines.put(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void closeBodyQuietly(HttpExchange ex) {
        try {
            InputStream in = ex.getRequestBody();
            if (in != null) {
                in.close();
            }
        } catch (Exception ignored) {
            // unblocks a stuck body read
        }
    }

    private static InputStream requestBody(HttpExchange ex) {
        InputStream in = ex.getRequestBody();
        return in == null ? InputStream.nullInputStream() : in;
    }

    /**
     * @return {@code false} when the session has failed and the reader should stop
     */
    private static boolean consumeLine(String line, SessionBox box, StreamEmitter emitter) {
        Object parsed;
        try {
            parsed = Json.parse(line);
        } catch (Exception e) {
            fail(box, emitter, "malformed NDJSON");
            return false;
        }
        if (!(parsed instanceof Map<?, ?>)) {
            fail(box, emitter, "malformed NDJSON");
            return false;
        }
        Map<String, Object> obj = Json.asMap(parsed);
        String type = Json.str(obj.get("type"));
        if ("open".equals(type)) {
            return onOpen(obj, box, emitter);
        }
        StreamInvocation session = box.session;
        if (session == null) {
            fail(box, emitter, "missing open");
            return false;
        }
        if (session.isInputComplete() || session.isFailed()) {
            return true;
        }
        String rid = Json.str(obj.get("requestId"));
        if (!rid.isBlank() && !rid.equals(session.requestId())) {
            fail(box, emitter, "requestId mismatch");
            return false;
        }
        if ("frame".equals(type)) {
            session.offerFrame(frameFrom(obj, session.requestId()));
            return true;
        }
        if ("close".equals(type)) {
            session.markInputComplete();
            return true;
        }
        fail(box, emitter, "malformed NDJSON");
        return false;
    }

    private static boolean onOpen(Map<String, Object> obj, SessionBox box, StreamEmitter emitter) {
        if (box.session != null) {
            fail(box, emitter, "duplicate open");
            return false;
        }
        String requestId = Json.str(obj.get("requestId"));
        String templateId = Json.str(obj.get("templateId"));
        if (requestId.isBlank()) {
            fail(box, emitter, "missing requestId");
            return false;
        }
        if (templateId.isBlank()) {
            fail(box, emitter, "missing templateId");
            return false;
        }
        if (!StudioQuackDispatch.validTemplateId(templateId)) {
            fail(box, emitter, "invalid templateId");
            return false;
        }
        Map<String, Object> input = Json.asMap(obj.get("input"));
        Map<String, Object> context = Json.asMap(obj.get("context"));
        box.session = new StreamInvocation(requestId, templateId, input, context);
        return true;
    }

    private static Frame frameFrom(Map<String, Object> obj, String requestId) {
        int seq = 0;
        Object seqObj = obj.get("seq");
        if (seqObj instanceof Number n) {
            seq = n.intValue();
        }
        return new Frame(
                requestId,
                Json.str(obj.get("port")),
                Json.str(obj.get("channel")),
                Json.str(obj.get("delta")),
                seq);
    }

    private static void fail(SessionBox box, StreamEmitter emitter, String message) {
        box.openFailed = box.session == null;
        StreamInvocation s = box.session;
        if (s != null) {
            s.fail(message);
        } else {
            box.openFailed = true;
        }
        emitter.error(message);
    }

    private static boolean openSse(HttpExchange ex) {
        try {
            ex.getResponseHeaders().set("Content-Type", SSE_CONTENT_TYPE);
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            // 0 = chunked; required so events flush before the node finishes.
            ex.sendResponseHeaders(200, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void drainSse(HttpExchange ex, ArrayBlockingQueue<OutEvent> outQ,
                                 AtomicBoolean terminal, long deadlineNanos) {
        OutputStream out;
        try {
            out = ex.getResponseBody();
        } catch (Exception e) {
            return;
        }
        boolean wroteDone = false;
        try {
            while (System.nanoTime() < deadlineNanos) {
                long remainMs = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
                if (remainMs <= 0) {
                    break;
                }
                OutEvent ev = outQ.poll(Math.min(250L, remainMs), TimeUnit.MILLISECONDS);
                if (ev == null) {
                    continue;
                }
                writeEvent(out, ev);
                if (ev.kind == OutEvent.Kind.COMPLETE || ev.kind == OutEvent.Kind.ERROR) {
                    writeDone(out);
                    wroteDone = true;
                    return;
                }
            }
            if (!terminal.get()) {
                writeEvent(out, OutEvent.error("timed out"));
            }
            if (!wroteDone) {
                writeDone(out);
            }
        } catch (Exception ignored) {
            // never throw
        } finally {
            try {
                out.flush();
            } catch (Exception ignored) {
                // never throw
            }
        }
    }

    private static void writeEvent(OutputStream out, OutEvent ev) throws IOException {
        if (ev == null) {
            return;
        }
        String data = ev.dataJson();
        StringBuilder sb = new StringBuilder();
        sb.append("event: ").append(ev.sseName()).append('\n');
        if (data != null) {
            sb.append("data: ").append(data).append('\n');
        }
        sb.append('\n');
        out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void writeDone(OutputStream out) throws IOException {
        out.write("event: done\n\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static void tryWriteFatal(HttpExchange ex, String message) {
        try {
            if (!openSse(ex)) {
                StudioQuackDispatch.sendJson(ex, 200, StudioQuackDispatch.dispatchFail(
                        StudioQuackDispatch.DISPATCH_ERROR, message));
                return;
            }
            OutputStream out = ex.getResponseBody();
            writeEvent(out, OutEvent.error(message));
            writeDone(out);
        } catch (Exception ignored) {
            // never throw
        } finally {
            try {
                ex.close();
            } catch (Exception ignoredClose) {
                // never throw
            }
        }
    }

    static String safeError(Exception e) {
        if (e == null) {
            return "error";
        }
        String message = e.getMessage() == null ? "" : e.getMessage();
        String shaped = e.getClass().getSimpleName() + ": " + message;
        return shaped.length() <= 500 ? shaped : shaped.substring(0, 500);
    }

    /** Sentinel objects (identity compare). Not valid NDJSON lines. */
    static final class LineRead {
        static final String TOO_LONG = new String("too-long");
        static final Object EOF = new Object();

        private LineRead() {
        }
    }

    /** Incremental NDJSON line scanner. Caps a single line at {@code maxChars}. */
    static final class LineScanner {
        private final Reader raw;
        private final char[] buf = new char[8192];
        private int pos;
        private int lim;

        LineScanner(Reader raw) {
            this.raw = raw;
        }

        /**
         * Next line without the terminator, {@code null} on EOF, or
         * {@link LineRead#TOO_LONG} when the line exceeds {@code maxChars}.
         */
        String readLine(int maxChars) throws IOException {
            StringBuilder sb = new StringBuilder();
            while (true) {
                if (pos >= lim) {
                    lim = raw.read(buf);
                    pos = 0;
                    if (lim < 0) {
                        return sb.length() == 0 ? null : sb.toString();
                    }
                }
                while (pos < lim) {
                    char c = buf[pos++];
                    if (c == '\n') {
                        return sb.toString();
                    }
                    if (c == '\r') {
                        continue;
                    }
                    if (sb.length() >= maxChars) {
                        drainToNewline();
                        return LineRead.TOO_LONG;
                    }
                    sb.append(c);
                }
            }
        }

        private void drainToNewline() throws IOException {
            while (true) {
                if (pos >= lim) {
                    lim = raw.read(buf);
                    pos = 0;
                    if (lim < 0) {
                        return;
                    }
                }
                while (pos < lim) {
                    if (buf[pos++] == '\n') {
                        return;
                    }
                }
            }
        }
    }

    private static final class SessionBox {
        volatile StreamInvocation session;
        volatile boolean openFailed;
    }

    private static final class OutEvent {
        enum Kind { PARTIAL, COMPLETE, ERROR }

        final Kind kind;
        final String channel;
        final String delta;
        final Map<String, Object> result;
        final String error;

        private OutEvent(Kind kind, String channel, String delta,
                         Map<String, Object> result, String error) {
            this.kind = kind;
            this.channel = channel;
            this.delta = delta;
            this.result = result;
            this.error = error;
        }

        static OutEvent partial(String channel, String delta) {
            return new OutEvent(Kind.PARTIAL, channel, delta, null, null);
        }

        static OutEvent complete(Map<String, Object> result) {
            return new OutEvent(Kind.COMPLETE, null, null, result == null ? Map.of() : result, null);
        }

        static OutEvent error(String message) {
            String m = (message == null || message.isBlank()) ? "error" : message;
            if (m.length() > 500) {
                m = m.substring(0, 500);
            }
            return new OutEvent(Kind.ERROR, null, null, null, m);
        }

        String sseName() {
            return switch (kind) {
                case PARTIAL -> "partial";
                case COMPLETE -> "complete";
                case ERROR -> "error";
            };
        }

        String dataJson() {
            return switch (kind) {
                case PARTIAL -> {
                    Map<String, Object> body = new LinkedHashMap<>();
                    body.put("channel", channel == null ? "" : channel);
                    body.put("delta", delta == null ? "" : delta);
                    yield Json.write(body);
                }
                case COMPLETE -> Json.write(result == null ? Map.of() : result);
                case ERROR -> Json.write(Map.of("error", error == null ? "error" : error));
            };
        }
    }

    private static final class QueueEmitter implements StreamEmitter {
        private final ArrayBlockingQueue<OutEvent> outQ;
        private final AtomicBoolean terminal;

        QueueEmitter(ArrayBlockingQueue<OutEvent> outQ, AtomicBoolean terminal) {
            this.outQ = outQ;
            this.terminal = terminal;
        }

        @Override
        public void emitPartial(String channel, String delta) {
            if (channel == null || channel.isBlank() || delta == null || terminal.get()) {
                return;
            }
            enqueue(OutEvent.partial(channel, delta), true);
        }

        @Override
        public void complete(Map<String, Object> result) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            enqueue(OutEvent.complete(result == null ? Map.of() : result), false);
        }

        @Override
        public void error(String message) {
            if (!terminal.compareAndSet(false, true)) {
                return;
            }
            enqueue(OutEvent.error(message), false);
        }

        private void enqueue(OutEvent ev, boolean dropOldest) {
            if (dropOldest) {
                while (!outQ.offer(ev)) {
                    outQ.poll();
                }
                return;
            }
            while (!outQ.offer(ev)) {
                outQ.poll();
            }
        }
    }
}
