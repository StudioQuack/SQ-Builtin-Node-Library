package com.studioquack.nodelibrary.nodes.openai;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idle-after-headers wrapper: stall vs clean EOF. No network. */
class OpenAiIdleStreamTest {

    @Test
    void silentStreamStallsWithinIdleWindow() throws Exception {
        AtomicBoolean stalled = new AtomicBoolean();
        InputStream hung = new InputStream() {
            private final Object lock = new Object();
            private volatile boolean closed;

            @Override
            public int read() throws IOException {
                synchronized (lock) {
                    while (!closed) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            throw new IOException("interrupted", e);
                        }
                    }
                    throw new IOException("closed");
                }
            }

            @Override
            public void close() {
                synchronized (lock) {
                    closed = true;
                    lock.notifyAll();
                }
            }
        };
        long t0 = System.nanoTime();
        try (InputStream watched = OpenAiIdleStream.watch(hung, Duration.ofMillis(200), stalled)) {
            assertThrows(IOException.class, watched::read);
        }
        assertTrue(stalled.get(), "watchdog must mark stall, not clean EOF");
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(elapsedMs < 3_000, "must fail fast, elapsedMs=" + elapsedMs);
    }

    @Test
    void eofAfterWatchdogCloseIsStillAStall() throws Exception {
        AtomicBoolean stalled = new AtomicBoolean();
        InputStream eofOnClose = new InputStream() {
            private final Object lock = new Object();
            private volatile boolean closed;

            @Override
            public int read() {
                synchronized (lock) {
                    while (!closed) {
                        try {
                            lock.wait();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return -1;
                        }
                    }
                    return -1;
                }
            }

            @Override
            public void close() {
                synchronized (lock) {
                    closed = true;
                    lock.notifyAll();
                }
            }
        };
        try (InputStream watched = OpenAiIdleStream.watch(eofOnClose, Duration.ofMillis(200), stalled)) {
            assertThrows(OpenAiIdleStream.StreamStalledException.class, watched::read);
        }
        assertTrue(stalled.get());
    }

    @Test
    void streamThatYieldsThenEofSucceeds() throws Exception {
        AtomicBoolean stalled = new AtomicBoolean();
        byte[] payload = "hello-sse\n".getBytes(StandardCharsets.UTF_8);
        try (InputStream watched = OpenAiIdleStream.watch(
                new ByteArrayInputStream(payload), Duration.ofSeconds(5), stalled)) {
            assertEquals("hello-sse\n", new String(watched.readAllBytes(), StandardCharsets.UTF_8));
        }
        assertFalse(stalled.get());
    }
}
