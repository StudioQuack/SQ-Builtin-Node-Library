package com.studioquack.nodelibrary.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Streaming splitter that lifts inline reasoning markup out of the answer channel.
 *
 * <p>Reasoning models (DeepSeek-R1, Qwen3, QwQ, …) wrap chain-of-thought in
 * {@code <think>…</think>}-style tags inside {@code content} when the server does
 * not separate it into {@code reasoning_content} (SGLang/vLLM without
 * {@code --reasoning-parser}, text-generation-webui, …). Two wire shapes exist:
 *
 * <ul>
 *   <li><b>Full tags</b> — {@code <think>…</think>answer}: split live, thinking
 *       streams to the thinking channel as tokens arrive.</li>
 *   <li><b>Missing opener</b> — the chat template pre-fills {@code <think>} into the
 *       prompt (SGLang/vLLM R1 templates), so the stream carries
 *       {@code reasoning…</think>answer} with no opening tag. When the whole content
 *       arrives in one piece (non-stream envelope) the split is immediate; in
 *       token-by-token streaming the pre-closer text has already shipped as answer
 *       deltas, so collectors repair the final payload via
 *       {@link #splitOrphanedCloser(String)} (the same rule Open WebUI applies).</li>
 * </ul>
 *
 * <p>Tags may straddle chunk boundaries; the longest tail that is still a tag
 * prefix is held back until it resolves. Request-scoped — never static state.
 */
public final class ThinkTagSplitter {

    public static final String THINKING = "thinking";
    public static final String ANSWER = "answer";

    /** One classified fragment. {@code channel} is {@link #THINKING} or {@link #ANSWER}. */
    public record Segment(String channel, String text) {
    }

    private static final List<String> OPEN_TAGS = List.of(
            "<think>", "<thinking>", "<thought>", "<reasoning>");
    private static final List<String> CLOSE_TAGS = List.of(
            "</think>", "</thinking>", "</thought>", "</reasoning>");

    private final StringBuilder buf = new StringBuilder();
    private boolean inThink;
    private boolean answerStarted;
    /** Set at a closing boundary: the template's blank line(s) after {@code </think>} are not answer text. */
    private boolean justClosed;

    /** Feed one content delta; returns the fragments that resolved. */
    public List<Segment> feed(String piece) {
        List<Segment> out = new ArrayList<>();
        if (piece != null) {
            buf.append(piece);
        }
        int i = 0;
        while (i < buf.length()) {
            int lt = indexOf(i);
            if (lt < 0) {
                break;
            }
            String tag = tagAt(lt);
            if (tag == null) {
                if (isTagPrefix(lt)) {
                    // Possible tag split across chunks — emit what precedes it, hold the tail.
                    emit(out, 0, lt, currentChannel());
                    buf.delete(0, lt);
                    return out;
                }
                i = lt + 1; // literal '<'
                continue;
            }
            boolean opener = isOpener(tag);
            if (opener && inThink) {
                // Nested opener is not a boundary — keep it as literal thinking text.
                emit(out, 0, lt + tag.length(), THINKING);
                buf.delete(0, lt + tag.length());
                i = 0;
                continue;
            }
            if (!opener && !inThink && answerStarted) {
                // Stray closer inside a running answer — literal text, never a boundary.
                emit(out, 0, lt + tag.length(), ANSWER);
                buf.delete(0, lt + tag.length());
                i = 0;
                continue;
            }
            // Boundary tag. Preceding text is thinking when a block is open, or when a
            // closer arrives before any answer (template pre-filled the opener away).
            String preceding = (opener && !inThink) ? ANSWER : THINKING;
            emit(out, 0, lt, preceding);
            buf.delete(0, lt + tag.length());
            i = 0;
            inThink = opener;
            if (!opener) {
                justClosed = true;
            }
        }
        if (buf.length() > 0) {
            emit(out, 0, buf.length(), currentChannel());
            buf.setLength(0);
        }
        return out;
    }

    /** Stream end: drain the remainder (an unclosed block flushes as thinking). */
    public List<Segment> flush() {
        List<Segment> out = new ArrayList<>();
        if (buf.length() > 0) {
            emit(out, 0, buf.length(), currentChannel());
            buf.setLength(0);
        }
        inThink = false;
        return out;
    }

    /** One-shot split of a complete content string (non-stream envelopes). */
    public static List<Segment> splitAll(String text) {
        ThinkTagSplitter s = new ThinkTagSplitter();
        List<Segment> out = new ArrayList<>(s.feed(text));
        out.addAll(s.flush());
        return out;
    }

    /**
     * Retroactive repair for token-streamed missing-opener output: the answer buffer
     * holds {@code reasoning…</think>answer} and no thinking was captured. Splits at
     * the first closer. Returns {@code [thinking, answer]} or {@code null} when the
     * text has no orphan closer (or an explicit opener precedes it).
     */
    public static String[] splitOrphanedCloser(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int idx = -1;
        String hit = null;
        for (String close : CLOSE_TAGS) {
            int at = lower.indexOf(close);
            if (at >= 0 && (idx < 0 || at < idx)) {
                idx = at;
                hit = close;
            }
        }
        if (idx < 0) {
            return null;
        }
        for (String open : OPEN_TAGS) {
            int at = lower.indexOf(open);
            if (at >= 0 && at < idx) {
                return null; // explicit block came first — inline splitting already ran
            }
        }
        String thinking = text.substring(0, idx);
        String answer = text.substring(idx + hit.length());
        // The closer is typically followed by the template's blank line(s).
        while (answer.startsWith("\r") || answer.startsWith("\n")) {
            answer = answer.substring(1);
        }
        return new String[]{thinking, answer};
    }

    private String currentChannel() {
        return inThink ? THINKING : ANSWER;
    }

    private void emit(List<Segment> out, int from, int to, String channel) {
        if (to <= from) {
            return;
        }
        String text = buf.substring(from, to);
        if (ANSWER.equals(channel) && justClosed) {
            // Swallow the newline run right after a closer; real answer text ends the strip.
            text = text.replaceFirst("^[\\r\\n]+", "");
            if (text.isEmpty()) {
                return;
            }
            justClosed = false;
        }
        if (text.isEmpty()) {
            return;
        }
        if (ANSWER.equals(channel)) {
            answerStarted = true;
        }
        out.add(new Segment(channel, text));
    }

    private int indexOf(int from) {
        for (int i = from; i < buf.length(); i++) {
            if (buf.charAt(i) == '<') {
                return i;
            }
        }
        return -1;
    }

    /** Complete tag starting at {@code at}, or {@code null}. Case-insensitive. */
    private String tagAt(int at) {
        for (String tag : OPEN_TAGS) {
            if (regionMatches(at, tag)) {
                return tag;
            }
        }
        for (String tag : CLOSE_TAGS) {
            if (regionMatches(at, tag)) {
                return tag;
            }
        }
        return null;
    }

    /** True when the buffer tail from {@code at} could still grow into a tag. */
    private boolean isTagPrefix(int at) {
        int len = buf.length() - at;
        for (String tag : OPEN_TAGS) {
            if (len < tag.length() && regionMatches(at, tag.substring(0, len))) {
                return true;
            }
        }
        for (String tag : CLOSE_TAGS) {
            if (len < tag.length() && regionMatches(at, tag.substring(0, len))) {
                return true;
            }
        }
        return false;
    }

    private boolean regionMatches(int at, String tag) {
        if (at + tag.length() > buf.length()) {
            return false;
        }
        for (int k = 0; k < tag.length(); k++) {
            char a = buf.charAt(at + k);
            char b = tag.charAt(k);
            if (a >= 'A' && a <= 'Z') {
                a = (char) (a + 32);
            }
            if (a != b) {
                return false;
            }
        }
        return true;
    }

    private static boolean isOpener(String tag) {
        return !tag.startsWith("</");
    }
}
