package com.studioquack.nodelibrary.nodes.openai;

import studioquack.Json;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Images / TTS / STT branches. Best-effort parity with the Python node.
 * Never logs the API key.
 */
final class OpenAiMedia {

    private static final Duration TIMEOUT = Duration.ofSeconds(300);

    private OpenAiMedia() {
    }

    static Map<String, Object> images(String apiKey, String model, List<Object> messages,
                                      Map<String, Object> options) throws Exception {
        String prompt = lastUserText(messages);
        if (prompt.isBlank()) {
            throw new IllegalArgumentException(
                    "Image generation requires a text prompt in the latest user message.");
        }
        String ml = model.toLowerCase();
        boolean gptImage = ml.startsWith("gpt-image");
        boolean dalle3 = ml.startsWith("dall-e-3");
        Map<String, Object> opts = options == null ? Map.of() : options;
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("prompt", prompt);
        int n = NodeParams.toIntOrNull(opts.get("n")) == null ? 1 : NodeParams.toIntOrNull(opts.get("n"));
        if (dalle3) {
            n = 1;
        } else {
            n = Math.min(10, Math.max(1, n));
        }
        body.put("n", n);
        String size = Json.str(opts.get("size")).trim().toLowerCase();
        if (gptImage) {
            body.put("size", List.of("1024x1024", "1024x1536", "1536x1024", "auto").contains(size) ? size : "auto");
        } else if (dalle3) {
            body.put("size", List.of("1024x1024", "1792x1024", "1024x1792").contains(size) ? size : "1024x1024");
        } else {
            body.put("size", List.of("256x256", "512x512", "1024x1024").contains(size) ? size : "1024x1024");
        }
        String quality = Json.str(opts.get("quality")).trim().toLowerCase();
        if (gptImage && List.of("low", "medium", "high", "auto").contains(quality)) {
            body.put("quality", quality);
        } else if (dalle3 && List.of("standard", "hd").contains(quality)) {
            body.put("quality", quality);
        }
        if (!gptImage) {
            String rf = Json.str(opts.get("response_format")).trim().toLowerCase();
            body.put("response_format", List.of("url", "b64_json").contains(rf) ? rf : "b64_json");
        }
        if (dalle3) {
            String style = Json.str(opts.get("style")).trim().toLowerCase();
            if (List.of("vivid", "natural").contains(style)) {
                body.put("style", style);
            }
        }
        if (gptImage) {
            String bg = Json.str(opts.get("background")).trim().toLowerCase();
            if (List.of("transparent", "opaque", "auto").contains(bg)) {
                body.put("background", bg);
            }
            String of = Json.str(opts.get("output_format")).trim().toLowerCase();
            if (List.of("png", "jpeg", "webp").contains(of)) {
                body.put("output_format", of);
            }
            Integer comp = NodeParams.toIntOrNull(opts.get("output_compression"));
            if (comp != null && comp >= 0 && comp <= 100) {
                body.put("output_compression", comp);
            }
            String mod = Json.str(opts.get("moderation")).trim().toLowerCase();
            if (List.of("low", "auto").contains(mod)) {
                body.put("moderation", mod);
            }
        }
        Map<String, Object> resp = postJson(apiKey, "https://api.openai.com/v1/images/generations", body);
        String mime = "image/png";
        String ext = "png";
        if (gptImage) {
            String of = Json.str(body.get("output_format"));
            if ("jpeg".equals(of)) {
                mime = "image/jpeg";
                ext = "jpg";
            } else if ("webp".equals(of)) {
                mime = "image/webp";
                ext = "webp";
            }
        }
        List<Object> files = new ArrayList<>();
        String revised = "";
        for (Object item : Json.asList(resp.get("data"))) {
            Map<String, Object> img = Json.asMap(item);
            if (img.get("revised_prompt") != null && revised.isEmpty()) {
                revised = Json.str(img.get("revised_prompt"));
            }
            String name = "generated_" + UUID.randomUUID().toString().substring(0, 8) + "." + ext;
            if (img.get("b64_json") != null) {
                files.add(Map.of("type", "image", "mime_type", mime, "source", "base64",
                        "data", img.get("b64_json"), "name", name));
            } else if (img.get("url") != null) {
                files.add(Map.of("type", "image", "mime_type", mime, "source", "url",
                        "data", img.get("url"), "name", name));
            }
        }
        String summary = "Generated " + files.size() + " image(s) for prompt.";
        if (!revised.isEmpty()) {
            summary += " Revised prompt: " + revised;
        }
        return assistant(summary, files);
    }

    static Map<String, Object> tts(String apiKey, String model, List<Object> messages,
                                   Map<String, Object> options) throws Exception {
        String text = lastUserText(messages);
        if (text.isBlank()) {
            throw new IllegalArgumentException("TTS requires a text input in the latest user message.");
        }
        Map<String, Object> opts = options == null ? Map.of() : options;
        String voice = Json.str(opts.get("voice"));
        if (voice.isBlank()) {
            voice = "alloy";
        }
        String fmt = Json.str(opts.get("format"));
        if (fmt.isBlank()) {
            fmt = "mp3";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("voice", voice);
        body.put("input", text);
        body.put("response_format", fmt);
        byte[] audio = postBytes(apiKey, "https://api.openai.com/v1/audio/speech", body);
        String b64 = Base64.getEncoder().encodeToString(audio);
        return assistant("Generated " + audio.length + " bytes of audio in " + fmt + ".",
                List.of(Map.of("type", "audio", "mime_type", "audio/" + fmt, "source", "base64",
                        "data", b64, "name", "tts_" + UUID.randomUUID().toString().substring(0, 8) + "." + fmt)));
    }

    static Map<String, Object> stt(String apiKey, String model, List<Object> messages,
                                   Map<String, Object> options) throws Exception {
        byte[] audio = extractAudio(messages);
        if (audio == null || audio.length == 0) {
            throw new IllegalArgumentException("STT requires an audio attachment in the latest user message.");
        }
        Map<String, Object> opts = options == null ? Map.of() : options;
        String boundary = "----sq" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = multipart(boundary, model, audio, Json.str(opts.get("language")),
                Json.str(opts.get("prompt")));
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.openai.com/v1/audio/transcriptions"))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp;
        try {
            resp = OpenAiConfig.HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw OpenAiConfig.wrapHttpTimeout(e, model, "/audio/transcriptions", t0);
        }
        if (resp.statusCode() >= 400) {
            throw new OpenAiConfig.ApiException(resp.statusCode(),
                    providerErrorMessage(resp.statusCode(), resp.body()), resp.body());
        }
        Object parsed = Json.parse(resp.body());
        String text = parsed instanceof Map<?, ?> m ? Json.str(Json.asMap(m).get("text")) : resp.body();
        return assistant(text, List.of());
    }

    static String lastUserText(List<Object> messages) {
        if (messages == null) {
            return "";
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> m = Json.asMap(messages.get(i));
            if (!"user".equals(Json.str(m.get("role")))) {
                continue;
            }
            Object c = m.get("content");
            if (c instanceof String s && !s.isBlank()) {
                return s;
            }
            StringBuilder sb = new StringBuilder();
            for (Object b : Json.asList(c)) {
                Map<String, Object> block = Json.asMap(b);
                if ("text".equals(Json.str(block.get("type")))) {
                    if (sb.length() > 0) {
                        sb.append('\n');
                    }
                    sb.append(Json.str(block.get("text")));
                }
            }
            if (sb.length() > 0) {
                return sb.toString();
            }
        }
        return "";
    }

    private static byte[] extractAudio(List<Object> messages) {
        for (Object raw : messages == null ? List.of() : messages) {
            Map<String, Object> m = Json.asMap(raw);
            if (!"user".equals(Json.str(m.get("role")))) {
                continue;
            }
            for (Object b : Json.asList(m.get("content"))) {
                Map<String, Object> block = Json.asMap(b);
                String type = Json.str(block.get("type"));
                if ("audio".equals(type) || "audio".equals(Json.str(block.get("kind")))) {
                    byte[] decoded = decode(firstData(block));
                    if (decoded != null && decoded.length > 0) {
                        return decoded;
                    }
                }
                // Folded FULL audio arrives as {type:input_audio, input_audio:{data, format}}.
                if ("input_audio".equals(type) && block.get("input_audio") instanceof Map<?, ?>) {
                    byte[] decoded = decode(firstData(Json.asMap(block.get("input_audio"))));
                    if (decoded != null && decoded.length > 0) {
                        return decoded;
                    }
                }
            }
            for (Object f : Json.asList(m.get("files"))) {
                Map<String, Object> file = Json.asMap(f);
                if ("audio".equalsIgnoreCase(Json.str(file.get("kind")))
                        || "audio".equalsIgnoreCase(Json.str(file.get("type")))) {
                    byte[] decoded = decode(firstData(file));
                    if (decoded != null && decoded.length > 0) {
                        return decoded;
                    }
                }
            }
        }
        return null;
    }

    private static String firstData(Map<String, Object> block) {
        String data = Json.str(block.get("data"));
        if (data.isBlank()) {
            data = Json.str(block.get("file_data"));
        }
        if (data.isBlank()) {
            data = Json.str(block.get("url"));
        }
        return data;
    }

    private static byte[] decode(String data) {
        if (data == null || data.isBlank()) {
            return null;
        }
        if (data.startsWith("data:") && data.contains(",")) {
            data = data.substring(data.indexOf(',') + 1);
        }
        try {
            return Base64.getDecoder().decode(data);
        } catch (studioquack.StudioQuackError e) {
            throw e;
        } catch (Exception e) {
            return null;
        }
    }

    private static byte[] multipart(String boundary, String model, byte[] audio, String language, String prompt) {
        String dash = "--" + boundary + "\r\n";
        StringBuilder sb = new StringBuilder();
        sb.append(dash).append("Content-Disposition: form-data; name=\"model\"\r\n\r\n")
                .append(model).append("\r\n");
        if (language != null && !language.isBlank()) {
            sb.append(dash).append("Content-Disposition: form-data; name=\"language\"\r\n\r\n")
                    .append(language).append("\r\n");
        }
        if (prompt != null && !prompt.isBlank()) {
            sb.append(dash).append("Content-Disposition: form-data; name=\"prompt\"\r\n\r\n")
                    .append(prompt).append("\r\n");
        }
        sb.append(dash).append("Content-Disposition: form-data; name=\"file\"; filename=\"input.wav\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        byte[] head = sb.toString().getBytes(StandardCharsets.UTF_8);
        byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        byte[] all = new byte[head.length + audio.length + tail.length];
        System.arraycopy(head, 0, all, 0, head.length);
        System.arraycopy(audio, 0, all, head.length, audio.length);
        System.arraycopy(tail, 0, all, head.length + audio.length, tail.length);
        return all;
    }

    private static Map<String, Object> postJson(String apiKey, String url, Map<String, Object> body)
            throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<String> resp;
        try {
            resp = OpenAiConfig.HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.net.http.HttpTimeoutException e) {
            throw OpenAiConfig.wrapHttpTimeout(e, NodeParams.str(body.get("model")),
                    "/images/generations", t0);
        }
        if (resp.statusCode() >= 400) {
            throw new OpenAiConfig.ApiException(resp.statusCode(),
                    providerErrorMessage(resp.statusCode(), resp.body()), resp.body());
        }
        Object parsed = Json.parse(resp.body());
        return Json.asMap(parsed);
    }

    private static byte[] postBytes(String apiKey, String url, Map<String, Object> body) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(TIMEOUT)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
        long t0 = System.nanoTime();
        HttpResponse<byte[]> resp;
        try {
            resp = OpenAiConfig.HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.net.http.HttpTimeoutException e) {
            throw OpenAiConfig.wrapHttpTimeout(e, NodeParams.str(body.get("model")),
                    "/audio/speech", t0);
        }
        if (resp.statusCode() >= 400) {
            String err = new String(resp.body() == null ? new byte[0] : resp.body(), StandardCharsets.UTF_8);
            throw new OpenAiConfig.ApiException(resp.statusCode(),
                    providerErrorMessage(resp.statusCode(), err), err);
        }
        return resp.body();
    }

    /** Package-visible so tests can assert provider JSON parsing without the network. */
    static String providerErrorMessage(int status, String body) {
        return OpenAiChat.openaiErrorMessage(status, body);
    }

    private static Map<String, Object> assistant(String text, List<Object> files) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("role", "assistant");
        out.put("content", List.of(Map.of("type", "text", "text", text == null ? "" : text)));
        if (files != null && !files.isEmpty()) {
            out.put("output_files", files);
        }
        out.put("finish_reason", "stop");
        return out;
    }
}
