package com.studioquack.nodelibrary.nodes.openai;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chat-model capability gate: leftover image parts vs vision, and whether
 * native PDF {@code file} parts are allowed. Image-gen / TTS / STT never
 * enter this table ({@link #shouldGate}).
 */
final class OpenAiCapabilities {

    private OpenAiCapabilities() {
        // Utility — never instantiated.
    }

    /**
     * Image-gen, TTS and STT skip the chat gate (their own early-return).
     */
    static boolean shouldGate(String modelLower) {
        String m = norm(modelLower);
        return !m.isEmpty()
                && !OpenAiConfig.isImageModel(m)
                && !OpenAiConfig.isTtsModel(m)
                && !OpenAiConfig.isSttModel(m);
    }

    /** Native PDF / {@code file} parts — same families as vision chat. */
    static boolean acceptsNativeDocuments(String modelLower) {
        return acceptsVision(modelLower);
    }

    /**
     * Vision (image / image_url). Prefix match; exclusions first (most-specific).
     */
    static boolean acceptsVision(String modelLower) {
        String m = norm(modelLower);
        if (m.isEmpty()) {
            return false;
        }
        // Never: image-gen, TTS, STT ids even when they share a gpt-4o / gpt-5 prefix.
        if (OpenAiConfig.isImageModel(m) || OpenAiConfig.isTtsModel(m) || OpenAiConfig.isSttModel(m)) {
            return false;
        }
        // Neither: text-only chat / reasoning snapshots.
        if (m.startsWith("o3-mini") || m.startsWith("o1-mini") || m.startsWith("o1-preview")
                || m.startsWith("gpt-3.5")) {
            return false;
        }
        // gpt-4o* (mini / audio-preview / realtime).
        if (m.startsWith("gpt-4o")) {
            return true;
        }
        // gpt-4.1*.
        if (m.startsWith("gpt-4.1")) {
            return true;
        }
        // gpt-4-turbo*, gpt-4-vision*.
        if (m.startsWith("gpt-4-turbo") || m.startsWith("gpt-4-vision")) {
            return true;
        }
        // gpt-5* chat (gpt-5, gpt-5.1, gpt-5.2*, gpt-5.4*, gpt-5.5*, gpt-5.6* sol/terra/luna).
        if (m.startsWith("gpt-5")) {
            return true;
        }
        // o1 / o1-* except o1-mini* / o1-preview* (excluded above).
        if (m.equals("o1") || m.startsWith("o1-")) {
            return true;
        }
        // o3 / o3-* except o3-mini* (excluded above).
        if (m.equals("o3") || m.startsWith("o3-")) {
            return true;
        }
        // o4-mini* (vision).
        if (m.startsWith("o4-mini")) {
            return true;
        }
        return false;
    }

    static String rejectImagesMessage(String model) {
        String shown = model == null || model.isBlank() ? "unknown" : model;
        return "model '" + shown
                + "' does not accept image inputs; use a vision-capable chat model such as gpt-5.5, gpt-5, gpt-4o";
    }

    /**
     * Shape-only inspect after fold+extract. Counts leftover image / native-file
     * parts and extracted-office text notices. Never looks at bytes or names.
     */
    static GateShape inspect(List<?> messages) {
        int images = 0;
        int documents = 0;
        int officeExtracted = 0;
        for (Object mObj : messages == null ? List.of() : messages) {
            Map<String, Object> m = NodeParams.asMap(mObj);
            Object content = m.get("content");
            if (content instanceof List<?> blocks) {
                for (Object b : blocks) {
                    Map<String, Object> part = NodeParams.asMap(b);
                    String type = NodeParams.trim(part.get("type")).toLowerCase(Locale.ROOT);
                    if (isImagePart(type, part)) {
                        images++;
                    } else if ("file".equals(type) || "document".equals(type) || "input_file".equals(type)) {
                        documents++;
                    } else if ("text".equals(type) && isExtractedOfficeNotice(NodeParams.str(part.get("text")))) {
                        officeExtracted++;
                    }
                }
            } else if (content instanceof String s && isExtractedOfficeNotice(s)) {
                officeExtracted++;
            }
            for (Object f : NodeParams.asList(m.get("files"))) {
                Map<String, Object> fm = NodeParams.asMap(f);
                String kind = NodeParams.trim(fm.getOrDefault("kind", fm.get("type"))).toLowerCase(Locale.ROOT);
                if ("image".equals(kind)) {
                    images++;
                } else if ("file".equals(kind) || "document".equals(kind)) {
                    documents++;
                }
            }
        }
        return new GateShape(images, documents, officeExtracted);
    }

    static void logDecision(String model, GateShape shape, String reject) {
        try {
            System.err.printf(
                    "openai.capability_gate model=%s images=%d documents=%d office_extracted=%d reject=%s%n",
                    model == null ? "" : model,
                    shape.images, shape.documents, shape.officeExtracted,
                    reject == null ? "none" : reject);
        } catch (Exception ignored) {
            // diagnostics must not break the node
        }
    }

    private static boolean isImagePart(String type, Map<String, Object> part) {
        if ("image".equals(type) || "image_url".equals(type) || "input_image".equals(type)) {
            return true;
        }
        return "image_url".equals(type) && part.get("image_url") instanceof Map<?, ?>;
    }

    private static boolean isExtractedOfficeNotice(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        return text.contains("extracted text]");
    }

    private static String norm(String modelLower) {
        return modelLower == null ? "" : modelLower.toLowerCase(Locale.ROOT).trim();
    }

    static final class GateShape {
        final int images;
        final int documents;
        final int officeExtracted;

        GateShape(int images, int documents, int officeExtracted) {
            this.images = images;
            this.documents = documents;
            this.officeExtracted = officeExtracted;
        }
    }
}
