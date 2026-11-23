package com.studioquack.nodelibrary.nodes.openai;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Idle-after-headers watchdog for OpenAI SSE / Responses bodies.
 *
 * <p>{@link java.net.http.HttpRequest#timeout} only covers the wait for
 * <em>response headers</em>. {@code BodyHandlers.ofInputStream()} then
 * {@code readLine()} can block forever on a silent-after-headers stream.
 * This wrapper arms a process-wide daemon scheduler; if no byte arrives
 * within the idle window the stream is closed so the parked read fails
 * (stall), distinct from a clean EOF.
 */
final class OpenAiIdleStream {

    private OpenAiIdleStream() {
        // Utility — never instantiated.
    }

    /**
     * Shared idle-watchdog scheduler: ONE daemon thread for the process
     * (holder idiom — tests that never stream never spawn it). Do not
     * create a thread per request.
     */
    private static final class IdleWatchdogScheduler {
        private static final ScheduledExecutorService INSTANCE =
                java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
                    Thread t = new Thread(r, "openai-stream-idle-watchdog");
                    t.setDaemon(true);
                    return t;
                });
    }

    /**
     * Wrap {@code in} so a silent stall closes the stream. {@code stalled}
     * is set only when the watchdog fires (not on clean EOF / caller close).
     */
    static InputStream watch(InputStream in, Duration idle, AtomicBoolean stalled) {
        if (in == null) {
            return null;
        }
        Duration window = idle == null || idle.isNegative() || idle.isZero()
                ? OpenAiConfig.STREAM_IDLE_TIMEOUT : idle;
        return new WatchedStream(in, window, stalled == null ? new AtomicBoolean() : stalled);
    }

    static final class StreamStalledException extends IOException {
        StreamStalledException(Duration idle) {
            super("OpenAI stream stalled: no bytes for " + idle.toSeconds() + "s after headers");
        }
    }

    private static final class WatchedStream extends FilterInputStream {
        private final Duration idle;
        private final long idleNanos;
        private final AtomicLong lastActivity = new AtomicLong(System.nanoTime());
        private final AtomicBoolean stalled;
        private final AtomicBoolean finished = new AtomicBoolean();
        private final AtomicReference<ScheduledFuture<?>> watchdog = new AtomicReference<>();

        private WatchedStream(InputStream in, Duration idle, AtomicBoolean stalled) {
            super(in);
            this.idle = idle;
            this.idleNanos = idle.toNanos();
            this.stalled = stalled;
            arm();
        }

        @Override
        public int read() throws IOException {
            try {
                int n = super.read();
                if (n >= 0) {
                    lastActivity.set(System.nanoTime());
                    return n;
                }
                // Some streams return -1 after watchdog close instead of throwing.
                throwIfStalled();
                return n;
            } catch (IOException e) {
                throw stalledOr(e);
            }
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            try {
                int n = super.read(b, off, len);
                if (n > 0) {
                    lastActivity.set(System.nanoTime());
                    return n;
                }
                if (n < 0) {
                    throwIfStalled();
                }
                return n;
            } catch (IOException e) {
                throw stalledOr(e);
            }
        }

        @Override
        public void close() throws IOException {
            finished.set(true);
            ScheduledFuture<?> pending = watchdog.getAndSet(null);
            if (pending != null) {
                pending.cancel(false);
            }
            super.close();
        }

        private void throwIfStalled() throws StreamStalledException {
            if (stalled.get()) {
                throw new StreamStalledException(idle);
            }
        }

        private IOException stalledOr(IOException e) throws IOException {
            throwIfStalled();
            throw e;
        }

        private void arm() {
            ScheduledExecutorService scheduler = IdleWatchdogScheduler.INSTANCE;
            Runnable task = new Runnable() {
                @Override
                public void run() {
                    if (finished.get()) {
                        return;
                    }
                    long remaining = lastActivity.get() + idleNanos - System.nanoTime();
                    if (remaining > 0L) {
                        try {
                            watchdog.set(scheduler.schedule(this, remaining, TimeUnit.NANOSECONDS));
                        } catch (RejectedExecutionException ignored) {
                            // JVM shutting down — the read is on its own.
                        }
                        return;
                    }
                    if (finished.compareAndSet(false, true)) {
                        stalled.set(true);
                        try {
                            in.close();
                        } catch (Exception ignored) {
                            // the parked read throws either way
                        }
                    }
                }
            };
            watchdog.set(scheduler.schedule(task, idleNanos, TimeUnit.NANOSECONDS));
        }
    }
}
