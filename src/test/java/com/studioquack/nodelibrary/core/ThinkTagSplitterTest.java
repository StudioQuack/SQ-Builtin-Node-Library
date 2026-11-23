package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ThinkTagSplitter: inline {@code <think>…</think>} lifting, chunk-split tags,
 * template-stripped orphan closers (SGLang/vLLM pre-fill), and the retroactive
 * {@link ThinkTagSplitter#splitOrphanedCloser(String)} repair.
 */
class ThinkTagSplitterTest {

    private static List<ThinkTagSplitter.Segment> run(String... pieces) {
        ThinkTagSplitter s = new ThinkTagSplitter();
        List<ThinkTagSplitter.Segment> out = new ArrayList<>();
        for (String p : pieces) {
            out.addAll(s.feed(p));
        }
        out.addAll(s.flush());
        return out;
    }

    private static String join(List<ThinkTagSplitter.Segment> segs, String channel) {
        StringBuilder sb = new StringBuilder();
        for (ThinkTagSplitter.Segment s : segs) {
            if (s.channel().equals(channel)) {
                sb.append(s.text());
            }
        }
        return sb.toString();
    }

    @Test
    void plainAnswerPassesThrough() {
        List<ThinkTagSplitter.Segment> segs = run("Hello, ", "world!");
        assertEquals("Hello, world!", join(segs, "answer"));
        assertEquals("", join(segs, "thinking"));
    }

    @Test
    void fullInlineBlockSplits() {
        List<ThinkTagSplitter.Segment> segs = run("<think>let me reason</think>The answer.");
        assertEquals("let me reason", join(segs, "thinking"));
        assertEquals("The answer.", join(segs, "answer"));
    }

    @Test
    void tagsSplitAcrossChunks() {
        List<ThinkTagSplitter.Segment> segs = run("<th", "ink>part1", "</th", "ink>", "ans", "wer");
        assertEquals("part1", join(segs, "thinking"));
        assertEquals("answer", join(segs, "answer"));
    }

    @Test
    void loneAngleBracketIsLiteral() {
        List<ThinkTagSplitter.Segment> segs = run("a < b and <thinkingx> stays");
        assertEquals("a < b and <thinkingx> stays", join(segs, "answer"));
        assertEquals("", join(segs, "thinking"));
    }

    @Test
    void missingOpenerOnePiece() {
        // SGLang/vLLM pre-fill: template injected <think>, stream starts mid-reasoning.
        // The template's blank line after the closer is not answer text.
        List<ThinkTagSplitter.Segment> segs = run("reasoning steps here</think>\n\nFinal answer.");
        assertEquals("reasoning steps here", join(segs, "thinking"));
        assertEquals("Final answer.", join(segs, "answer"));
    }

    @Test
    void missingOpenerStreamedKeepsCloserLiteralLive() {
        // Token-by-token the pre-closer text ships as answer (no retraction primitive);
        // the final payload is repaired by splitOrphanedCloser at the collector.
        List<ThinkTagSplitter.Segment> segs = run("reasoning ", "steps", "</think>", "Answer");
        assertEquals("reasoning steps</think>Answer", join(segs, "answer"));
    }

    @Test
    void strayCloserMidAnswerIsLiteral() {
        // Once answer text has streamed, a later closer can no longer be an orphan —
        // it stays literal answer text. (A closer before ANY answer is the SGLang
        // missing-opener case and reclassifies the head as thinking.)
        List<ThinkTagSplitter.Segment> segs = run("The answer starts. ", "A </think> tag is literal ");
        assertEquals("The answer starts. A </think> tag is literal ", join(segs, "answer"));
        assertEquals("", join(segs, "thinking"));
    }

    @Test
    void unclosedBlockFlushesAsThinking() {
        List<ThinkTagSplitter.Segment> segs = run("<think>truncated reasoning");
        assertEquals("truncated reasoning", join(segs, "thinking"));
        assertEquals("", join(segs, "answer"));
    }

    @Test
    void multipleBlocks() {
        List<ThinkTagSplitter.Segment> segs = run("<think>one</think>A<thinking>two</thinking>B");
        assertEquals("onetwo", join(segs, "thinking"));
        assertEquals("AB", join(segs, "answer"));
    }

    @Test
    void caseInsensitive() {
        List<ThinkTagSplitter.Segment> segs = run("<THINK>up</THINK>low");
        assertEquals("up", join(segs, "thinking"));
        assertEquals("low", join(segs, "answer"));
    }

    @Test
    void emptyAndNullFeeds() {
        ThinkTagSplitter s = new ThinkTagSplitter();
        assertTrue(s.feed(null).isEmpty());
        assertTrue(s.feed("").isEmpty());
        assertTrue(s.flush().isEmpty());
    }

    @Test
    void orphanCloserSplit() {
        String[] r = ThinkTagSplitter.splitOrphanedCloser("reasoning</think>\n\nAnswer.");
        assertEquals("reasoning", r[0]);
        assertEquals("Answer.", r[1]);
    }

    @Test
    void orphanCloserSplitLeadingCloser() {
        // Qwen3 no-think: content opens with the closer, no reasoning at all.
        String[] r = ThinkTagSplitter.splitOrphanedCloser("</think>\n\nAnswer.");
        assertEquals("", r[0]);
        assertEquals("Answer.", r[1]);
    }

    @Test
    void orphanCloserSplitIgnoresExplicitBlocks() {
        assertNull(ThinkTagSplitter.splitOrphanedCloser("<think>r</think>a </think> b"));
        assertNull(ThinkTagSplitter.splitOrphanedCloser("plain answer, no tags"));
        assertNull(ThinkTagSplitter.splitOrphanedCloser(null));
        assertNull(ThinkTagSplitter.splitOrphanedCloser(""));
    }

    @Test
    void splitAllOneShot() {
        List<ThinkTagSplitter.Segment> segs = ThinkTagSplitter.splitAll("<think>r</think>A");
        assertEquals("r", join(segs, "thinking"));
        assertEquals("A", join(segs, "answer"));
    }
}
