package com.studioquack.nodelibrary.nodes.officialapi;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SemanticScholarNodeTest {

    @Test
    void normalizePaperIdCanonicalizesArxivPrefix() {
        assertEquals("ArXiv:1706.03762", SemanticScholarNode.normalizePaperId("ARXIV:1706.03762"));
        assertEquals("ArXiv:1706.03762", SemanticScholarNode.normalizePaperId("arxiv:1706.03762"));
    }

    @Test
    void normalizePaperIdLeavesHexIdUntouched() {
        assertEquals("204e3073870fae3d05bcbc2f6a8e263d9b72e776",
                SemanticScholarNode.normalizePaperId("204e3073870fae3d05bcbc2f6a8e263d9b72e776"));
    }

    @Test
    void normalizePaperIdNullSafe() {
        assertNull(SemanticScholarNode.normalizePaperId(null));
    }
}
