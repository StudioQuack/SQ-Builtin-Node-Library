package com.studioquack.nodelibrary.nodes.ai;

import com.studioquack.nodelibrary.core.NodeCatalog;
import com.studioquack.nodelibrary.core.NodeRegistry;
import com.studioquack.nodelibrary.nodes.aiagent.AiAgentNode;
import com.studioquack.nodelibrary.nodes.localllm.LocalLlmNode;
import com.studioquack.nodelibrary.nodes.openai.OpenAiModelsNode;

/**
 * Registers the three AI-tier in-process handlers (AI Agent, OpenAI Models, Local LLM).
 *
 * <p>{@link com.studioquack.nodelibrary.nodes.NodeHandlers#registerAll} invokes this.
 * Template ids: catalog JSON {@code _id}, then {@code SQ_TEMPLATE_ID_*}, then the hardcoded fallback.
 */
public final class AiNodeHandlers {

    /** Fallback when catalog/env omit the id; must match {@code nodes/ai-agent} {@code _id}. */
    static final String AI_AGENT_DEFAULT_ID = "a72d6175-f0d5-48d7-94b4-cb50fa9acaf7";
    /** Fallback when catalog/env omit the id; must match {@code nodes/openai-models} {@code _id}. */
    static final String OPENAI_MODELS_DEFAULT_ID = "4f682e07-d874-4d10-abb3-0174ca65ec19";
    /** Fallback when catalog/env omit the id; must match {@code nodes/local-llm} {@code _id}. */
    static final String LOCAL_LLM_DEFAULT_ID = "9462f700-00cc-4e9c-872d-97a97aba984c";

    private AiNodeHandlers() {
        // registrar — never instantiated
    }

    /**
     * Wire AI Agent, OpenAI Models, and Local LLM. Null registry is ignored.
     */
    public static void register(NodeRegistry r) {
        if (r == null) {
            return;
        }
        NodeCatalog cat = NodeCatalog.load();
        String ai = firstNonBlank(cat.templateId("ai-agent"), System.getenv("SQ_TEMPLATE_ID_AI_AGENT"),
                AI_AGENT_DEFAULT_ID);
        String oai = firstNonBlank(cat.templateId("openai-models"), System.getenv("SQ_TEMPLATE_ID_OPENAI_MODELS"),
                OPENAI_MODELS_DEFAULT_ID);
        String loc = firstNonBlank(cat.templateId("local-llm"), System.getenv("SQ_TEMPLATE_ID_LOCAL_LLM"),
                LOCAL_LLM_DEFAULT_ID);
        r.register(new AiAgentNode(ai));
        r.register(new OpenAiModelsNode(oai));
        r.register(new LocalLlmNode(loc));
    }

    static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
