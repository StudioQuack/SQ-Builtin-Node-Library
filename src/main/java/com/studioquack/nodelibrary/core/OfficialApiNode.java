package com.studioquack.nodelibrary.core;

import studioquack.Json;
import studioquack.StudioQuackError;
import studioquack.StudioQuackHelper;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Spec-driven official-API node. One instance per {@code nodes/<key>/api_spec.json}.
 *
 * <p>When an operation declares a known {@code stream} block and streaming is not
 * disabled (input {@code stream}/{@code streaming} overrides config; default ON),
 * the node POSTs with {@code Accept: text/event-stream}, fans token deltas through
 * {@code helper.emitPartial(port, channel, delta)}, and returns a v2 assistant
 * payload on the stream port. Request bodies for those operations run through
 * {@link ProviderRequestMapper} on both the streaming and single-shot paths so
 * Gemini/Anthropic/Ollama/Cohere receive a native envelope. LLM-shaped
 * single-shot responses use the same {@code llm_output} key as streaming
 * ({@code status}/{@code data} plus {@code result}/{@code outputFiles});
 * specs with no stream block stay on {@code output}.
 *
 * <p>Inbound StudioQuack webhooks are BPMN message events — operations with
 * {@code kind=ingress} (listen/trigger) never open a socket; they tell the
 * operator to use a message start / catch event.
 */
public final class OfficialApiNode implements NodeHandler {

    private static final Pattern PATH_VAR = Pattern.compile("\\{([a-zA-Z0-9_]+)}");
    /** SurveyMonkey path segments are positive integer ids (no leading zeros). */
    private static final Pattern SURVEY_MONKEY_SURVEY_ID = Pattern.compile("^[1-9][0-9]*$");
    private static final Pattern VIMEO_VIDEO_ID = Pattern.compile("^[0-9]+$");
    private static final int VIMEO_MAX_PAGE = 10_000;
    private static final Pattern EMELIA_OBJECT_ID = Pattern.compile("^[a-f0-9]{24}$");
    /** Amplitude Export hours are UTC {@code YYYYMMDDTHH}. */
    private static final Pattern AMPLITUDE_EXPORT_HOUR = Pattern.compile("\\d{8}T(?:[01]\\d|2[0-3])");
    private static final DateTimeFormatter AMPLITUDE_EXPORT_HOUR_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HH");
    private static final long AMPLITUDE_EXPORT_MAX_HOURS = 365L * 24L;
    /**
     * These placeholders are a path, not one segment. {@code /} stays a separator.
     * A single id (the default) still encodes {@code /} as {@code %2F}.
     */
    private static final Set<String> SEGMENT_PATH_KEYS = Set.of(
            "dataset", "jobPath", "authPath", "tokenPath", "introspectionPath", "revokePath");
    /** Codecov commit id in URL path — API expects a full 40-character Git object id (hex). */
    private static final Pattern CODECOV_PATH_SHA = Pattern.compile("^[0-9a-fA-F]{40}$");
    /** Polygon aggregates path segments {@code from}/{@code to} — API expects YYYY-MM-DD (not MM/DD/YYYY). */
    private static final Pattern POLYGON_AGG_DATE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final List<String> INGRESS_NAMES = List.of(
            "listen", "trigger", "trigger.listen", "webhook_listen", "webhook");
    /** Calendly GET /event_types and GET /scheduled_events require user= or organization=; auto-fill from /users/me. */
    private static final Set<String> CALENDLY_ME_PREFILL_OPS = Set.of(
            "list-event-types", "list-events");

    private final String templateId;
    private final OfficialApiSpec spec;
    private final Clock clock;
    /** Calendly: cache PAT → current user URI from GET /users/me (short-lived helper, one instance per template). */
    private final ConcurrentHashMap<String, String> calendlyUserUriByToken = new ConcurrentHashMap<>();
    /** Package tests: last user URI injected by {@link #mergeCalendlyUserFromMeIfNeeded}. */
    volatile String lastResolvedCalendlyUserUri;

    public OfficialApiNode(String templateId, OfficialApiSpec spec) {
        this(templateId, spec, Clock.systemUTC());
    }

    /** Package-visible so tests can freeze {@code {$now}} / SigV4 timestamps. */
    OfficialApiNode(String templateId, OfficialApiSpec spec, Clock clock) {
        this.templateId = templateId;
        this.spec = spec;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.lastResolvedCalendlyUserUri = null;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    @Override
    public Map<String, Object> handle(Map<String, Object> request, StudioQuackHelper helper) {
        if (spec == null) {
            throw new StudioQuackError("NODE_ERROR", "api_spec.json missing for this node");
        }
        Map<String, Object> cfg = DataSupport.readConfig(request);
        if ("twilio".equalsIgnoreCase(spec.key)) {
            cfg = mergeTwilioConfig(cfg);
        }
        Map<String, Object> rawIn = flattenNestedToolInput(mergeToolCallArgs(DataSupport.readInput(request, helper)));
        Map<String, Object> rawLlm = flattenNestedToolInput(
                mergeToolCallArgs(DataSupport.readNamedPort(request, helper, "llm_input")));
        Map<String, Object> in;
        Map<String, Object> llmIn;
        if ("cohere".equalsIgnoreCase(spec.key)) {
            Map<String, Object> unified = mergeCohereUnifiedPort(rawIn, rawLlm);
            in = unified;
            llmIn = unified;
        } else if ("minimax".equalsIgnoreCase(spec.key)) {
            Map<String, Object> unified = mergeMinimaxUnifiedPort(rawIn, rawLlm);
            in = unified;
            llmIn = unified;
        } else {
            in = rawIn;
            llmIn = rawLlm;
        }
        if ("tally".equalsIgnoreCase(spec.key)) {
            in = mergeTallyInputs(spec.key, null, in);
            llmIn = mergeTallyInputs(spec.key, null, llmIn);
        }
        if ("tink".equalsIgnoreCase(spec.key)) {
            in = mergeTinkInputs(spec.key, null, in);
            llmIn = mergeTinkInputs(spec.key, null, llmIn);
        }
        // LLM FUNCTION port wins over instance `operation` so image_gen/chat switches
        // on llm_input are not shadowed by a default `operation` in configuration.
        String opName = resolveOperationName(llmIn, in, cfg);
        if ("cohere".equalsIgnoreCase(spec.key)) {
            Map<String, Object> cohereProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(cohereProbe);
            String inferred = inferCohereOperation(opName, cohereProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        if ("minimax".equalsIgnoreCase(spec.key)) {
            Map<String, Object> mmProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(mmProbe);
            String inferred = inferMinimaxOperation(opName, mmProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        if ("stability-ai".equalsIgnoreCase(spec.key)) {
            Map<String, Object> stabProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(stabProbe);
            String inferred = inferStabilityOperation(opName, stabProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        if ("groq".equalsIgnoreCase(spec.key)) {
            Map<String, Object> groqProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(groqProbe);
            String inferred = inferGroqOperation(opName, groqProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        if ("gladia".equalsIgnoreCase(spec.key)) {
            Map<String, Object> gladiaProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(gladiaProbe);
            String inferred = inferGladiaOperation(opName, gladiaProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        if ("ideogram".equalsIgnoreCase(spec.key)) {
            Map<String, Object> ideogramProbe = OfficialApiStream.mergeInput(in, llmIn);
            boolean explicitOp = cohereExplicitOperation(ideogramProbe);
            String inferred = inferIdeogramOperation(opName, ideogramProbe, cfg, explicitOp);
            if (inferred != null && !explicitOp) {
                opName = inferred;
            }
        }
        OfficialApiSpec.Operation op = spec.operation(opName);
        if (op == null) {
            throw invalidInput("Unknown operation: " + opName + ". Accepted: " + spec.operations.keySet());
        }
        if (isIngress(op)) {
            throw invalidInput(
                    "Inbound events are not a node operation. Use a BPMN message start / "
                            + "intermediate catch / boundary message event; the StudioQuack webhook "
                            + "ingress correlates onto those events.");
        }
        boolean stream = OfficialApiStream.enabled(op.stream, in, llmIn, cfg);
        // LLM nodes expose only llm_input, so non-streaming ops (embed, tokenize, models)
        // must read it as well; non-LLM nodes have no llm_input and keep `in` unchanged.
        Map<String, Object> bodySrc = OfficialApiStream.mergeInput(in, llmIn);
        bodySrc = mergeCohereEmbedTextsIfNeeded(op, bodySrc);
        if ("cohere".equalsIgnoreCase(spec.key)) {
            bodySrc = mergeCohereEmbedDefaults(op, bodySrc);
            bodySrc = mergeCohereOperationModel(op, bodySrc, cfg);
        }
        if (op.stream != null) {
            // LLM-shaped ops declare provider request fields in bodyFrom, but those values
            // (model, sampling knobs) live in node config while callers send only the v2
            // payload (messages/tools). Underlay config beneath the port payload so
            // bodyFrom resolves them; port values win, and the bodyFrom whitelist means
            // non-body config (api_key, timeouts) can never leak into the wire body.
            bodySrc = OfficialApiStream.mergeInput(cfg, bodySrc);
        }
        bodySrc = mergeCloudConvertAliases(spec.key, bodySrc);
        bodySrc = mergeBitbucketPathAliases(bodySrc);
        bodySrc = mergeCommonPathIdAliases(bodySrc);
        bodySrc = mergeCommonAgentPayloadFields(op, bodySrc);
        bodySrc = mergeCurrentsInputAliases(spec.key, bodySrc);
        bodySrc = mergeLemlistInputs(spec.key, op, bodySrc);
        bodySrc = mergeMixpanelInputs(spec.key, op, bodySrc);
        bodySrc = mergeNewRelicInputs(spec.key, op, bodySrc);
        bodySrc = mergeNewRelicEventIngest(spec.key, op, bodySrc);
        bodySrc = mergePostmarkInputs(spec.key, op, bodySrc);
        bodySrc = mergeShippoInputs(spec.key, op, bodySrc);
        bodySrc = mergeShodanInputs(spec.key, op, bodySrc);
        bodySrc = mergeSonarQubeInputs(spec.key, op, bodySrc);
        bodySrc = mergeSliteInputs(spec.key, op, bodySrc);
        bodySrc = mergeTallyInputs(spec.key, op, bodySrc);
        bodySrc = mergeTinkInputs(spec.key, op, bodySrc);
        bodySrc = mergeCalComInputs(spec.key, op, bodySrc);
        bodySrc = mergeVimeoInputs(spec.key, op, bodySrc);
        bodySrc = mergeEmeliaInputs(spec.key, op, bodySrc);
        bodySrc = mergeJinaInputs(spec.key, op, bodySrc);
        bodySrc = mergeJiraInputs(spec.key, op, bodySrc);
        bodySrc = mergeGitlabInputs(spec.key, op, bodySrc);
        bodySrc = mergeSupabaseInputs(spec.key, op, bodySrc);
        bodySrc = mergeTodoistInputs(spec.key, op, bodySrc);
        bodySrc = mergeTursoInputs(spec.key, op, bodySrc, cfg);
        for (String req : op.required) {
            if (blank(bodySrc, req) && blank(cfg, req)) {
                if (op.mime != null && req.equals(op.mime.target)) {
                    if (blank(bodySrc, "to") && blank(cfg, "to")) {
                        throw invalidInput("to (or " + req + ") is required for operation " + op.name);
                    }
                    continue;
                }
                throw invalidInput(req + " is required for operation " + op.name
                        + " (set on INPUT or in configurationParameters)");
            }
        }
        validateCurrentsAvailableCatalog(op, bodySrc, cfg);
        validateCodecovSha(op, bodySrc, cfg);
        validatePolygonAggregateDates(op, bodySrc, cfg);
        validateSurveyMonkeySurveyId(op, bodySrc, cfg);
        validateVimeoInputs(op, bodySrc, cfg);
        validateEmeliaInputs(op, bodySrc, cfg);
        validateMinimaxInputs(op, bodySrc, cfg);
        validateUnstructuredInputs(op, bodySrc, cfg);
        validateCalComApiKey(cfg);
        validateCalComCreateBooking(op, bodySrc, cfg);
        validateCalComBookingMutations(op, bodySrc, cfg);
        validateAtlassianBasicEmailConfig(cfg);
        validateUsCensusInputs(op, bodySrc, cfg);
        validateCustomerIoSearchLimit(op, bodySrc, cfg);
        validateIntercomConfig(cfg);
        validateJinaInputs(op, bodySrc, cfg);
        validateTodoistInputs(op, bodySrc, cfg);
        validateTrelloInputs(op, bodySrc, cfg);
        validateSupabaseInputs(op, bodySrc, cfg);
        validateTwilioInputs(op, bodySrc, cfg);
        validateWebflowInputs(op, bodySrc, cfg);
        validateEurostatInputs(op, bodySrc, cfg);
        validateFredInputs(op, bodySrc, cfg);
        validateFredApiKey(cfg);
        validateGladiaInputs(op, bodySrc, cfg);
        validateGumroadInputs(op, bodySrc, cfg);
        validateHashnodeInputs(op, bodySrc, cfg);
        validateHunterInputs(op, bodySrc, cfg);
        validateIdeogramInputs(op, bodySrc, cfg);
        validateTursoInputs(op, bodySrc, cfg);
        validateTursoBaseUrl(cfg);
        if ("amplitude".equalsIgnoreCase(spec.key)) {
            bodySrc = mergeAmplitudeIdentification(op, bodySrc);
            validateAmplitudeExport(op, bodySrc, cfg);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        boolean skipAuth = "tink".equalsIgnoreCase(spec.key) && op != null && "authorize".equals(op.name);
        if (!skipAuth) {
            String authErr = applyAuth(headers, cfg);
            if (authErr != null) {
                throw invalidInput(authErr);
            }
        }
        int timeout = DataSupport.cfgInt(cfg, spec.timeoutConfigKey, spec.defaultTimeout);
        bodySrc = mergeCalendlyUserFromMeIfNeeded(op, bodySrc, cfg, headers, timeout);
        Instant now = Instant.now(clock);
        // top extraHeaders → op extraHeaders → stream.extraHeaders (last). Unresolvable
        // {placeholders} omit the header; {$now:rfc1123}/{$now:iso8601} use {@code now}.
        headers.putAll(OfficialApiHeaders.interpolate(
                OfficialApiHeaders.merge(spec, op), bodySrc, cfg, now));
        String url;
        try {
            url = buildUrl(op, bodySrc, cfg, stream ? op.stream : null);
        } catch (IllegalArgumentException e) {
            throw invalidInput(e.getMessage());
        }
        try {
            java.net.http.HttpRequest.newBuilder(URI.create(url));
        } catch (IllegalArgumentException e) {
            throw invalidInput("invalid URL");
        }
        Map<String, String> cfgTypes = DataSupport.readConfigTypes(request);
        Map<String, Object> wireBody = mergeBodyDefaults(op, underlayBodyConfig(op, bodySrc, cfg, cfgTypes));
        wireBody = OfficialApiBody.coerceCohereNumericJsonFields(spec.key, op, wireBody);
        wireBody = OfficialApiBody.coerceOpenAiCompatibleNumericFields(spec.key, op, wireBody);
        if (stream) {
            OfficialApiBody.Prepared prepared = prepareStreamBody(op, wireBody, timeout);
            if (prepared.failed()) {
                throw new StudioQuackError("REQUEST_FAILED", prepared.error());
            }
            if (prepared.contentType() != null && !prepared.contentType().isBlank()) {
                headers.put("Content-Type", prepared.contentType());
            }
            String signErr = applySigV4(headers, op.method, url, prepared.bytes(), cfg, bodySrc, now);
            if (signErr != null) {
                throw invalidInput(signErr);
            }
            try {
                return OfficialApiStream.execute(
                        op.stream, op.method, url, headers, prepared.bytes(), timeout, helper);
            } catch (StudioQuackError e) {
                throw new StudioQuackError(e.errorCode(), redactSecrets(e.getMessage(), cfg, cfgTypes), e.data());
            }
        }
        OfficialApiBody.Prepared prepared = new OfficialApiBody.Prepared(new byte[0], null, null);
        boolean sendBody = shouldSendBody(op);
        if (sendBody) {
            prepared = OfficialApiBody.encode(op, wireBody, timeout);
            if (prepared.failed()) {
                throw new StudioQuackError("REQUEST_FAILED", prepared.error());
            }
            if (prepared.contentType() != null && !prepared.contentType().isBlank()) {
                headers.put("Content-Type", prepared.contentType());
            }
        }
        byte[] payload = sendBody ? prepared.bytes() : null;
        String signErr = applySigV4(headers, op.method, url, payload == null ? new byte[0] : payload,
                cfg, in, now);
        if (signErr != null) {
            throw invalidInput(signErr);
        }
        DataSupport.HttpResp resp = DataSupport.exchangeBytes(null, op.method, url, headers, payload, timeout);
        if (resp.error() != null && !resp.error().isBlank()) {
            String err = enrichTransportError(resp.error(), url);
            throw new StudioQuackError("REQUEST_FAILED", redactSecrets(err, cfg, cfgTypes));
        }
        if ("hashnode".equalsIgnoreCase(spec.key)) {
            rejectHashnodeNonGraphqlResponse(resp);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        String portKey = outputPortKey(spec, op);
        if (resp.status() >= 200 && resp.status() < 300) {
            out.put("status", "ok");
            out.put("http_status", resp.status());
            Object parsed = DataSupport.parseJson(resp.body());
            Object data = parsed == null ? resp.body() : normalizeCurrentsNewsPayload(spec.key, parsed);
            if ("cal-com".equalsIgnoreCase(spec.key)) {
                rejectCalComHollowBookingResponse(op, resp, data);
            }
            if ("mixpanel".equalsIgnoreCase(spec.key)) {
                rejectMixpanelIngestFailure(op, data);
            }
            out.put("data", data);
            if (!"output".equals(portKey)) {
                Object result = unaryResult(data);
                out.put("result", result == null ? data : result);
                List<?> files = outputFilesOf(out.get("result"), data);
                if (files != null && !files.isEmpty()) {
                    // Publish files ONCE (the dedicated file field); drop them from the result
                    // copy so the run-log panels do not render the same file twice.
                    Object res = out.get("result");
                    if (res instanceof Map<?, ?> resMap) {
                        Map<String, Object> stripped = new LinkedHashMap<>();
                        resMap.forEach((k, v) -> {
                            if (k != null && !"output_files".equals(k.toString())
                                    && !"outputFiles".equals(k.toString())) {
                                stripped.put(k.toString(), v);
                            }
                        });
                        out.put("result", stripped);
                    }
                    out.put("outputFiles", files);
                }
            }
        } else {
            // Providers echo rejected keys back ("invalid key sk-…"); never surface them.
            String msg = redactSecrets(DataSupport.httpStatusMessage(resp.status(), resp.body()), cfg, cfgTypes);
            if ("cal-com".equalsIgnoreCase(spec.key)) {
                msg = clarifyCalComHttpStatusMessage(msg);
            }
            if ("bitbucket".equalsIgnoreCase(spec.key)) {
                msg = clarifyBitbucketHttpStatusMessage(msg);
            }
            if ("invoice-ninja".equalsIgnoreCase(spec.key)) {
                msg = clarifyInvoiceNinjaHttpStatusMessage(msg);
            }
            if ("jira".equalsIgnoreCase(spec.key) || "confluence".equalsIgnoreCase(spec.key)) {
                msg = clarifyAtlassianCloudHttpStatusMessage(msg, resp.status());
            }
            if ("new-relic".equalsIgnoreCase(spec.key)) {
                msg = clarifyNewRelicHttpStatusMessage(msg, resp.status());
            }
            throw new StudioQuackError(DataSupport.httpErrorCode(resp.status()), msg,
                    Map.of("http_status", resp.status()));
        }
        return DataSupport.ports(portKey, out);
    }

    /** Redacts the auth credentials plus every password-typed config value (secret extraHeaders). */
    private String redactSecrets(String message, Map<String, Object> cfg, Map<String, String> cfgTypes) {
        List<String> secrets = new ArrayList<>();
        secrets.add(DataSupport.cfgStr(cfg, spec.authConfigKey, null));
        secrets.add(DataSupport.cfgStr(cfg, spec.secretKeyConfigKey, null));
        // Basic auth may carry the API key as the username (Campaign Monitor apiKey:x).
        secrets.add("basic".equals(spec.authType) ? DataSupport.cfgStr(cfg, spec.authUserKey, null) : null);
        secrets.add(spec.sigv4 == null ? null : DataSupport.cfgStr(cfg, spec.sigv4.sessionTokenConfigKey, null));
        if (cfgTypes != null) {
            cfgTypes.forEach((k, type) -> {
                if ("password".equals(type)) {
                    secrets.add(DataSupport.cfgStr(cfg, k, null));
                }
            });
        }
        return DataSupport.redact(message, secrets.toArray(String[]::new));
    }

    /**
     * Port key for a unary Official-API result.
     * <ol>
     *   <li>this op's {@code stream.port} when non-blank (usually {@code llm_output});</li>
     *   <li>else any sibling op's {@code stream.port} when non-blank (files_upload on
     *       Anthropic still lands on {@code llm_output});</li>
     *   <li>else the catalog key is one of the 22 LLM providers (Bedrock has
     *       {@code llm_output} but no {@code stream} block);</li>
     *   <li>else {@code output} (unstructured / files / transform / Openalex / jina-ai).</li>
     * </ol>
     */
    static final Set<String> LLM_NODE_KEYS = Set.of(
            "anthropic-claude", "google-gemini", "google-vertex-ai", "azure-openai",
            "groq", "cohere", "mistral", "perplexity", "moonshot-kimi", "xai-grok",
            "together-ai", "aws-bedrock", "vercel-ai-gateway", "hugging-face",
            "openrouter", "fireworks", "nvidia-nim", "minimax", "qwen-cloud",
            "ollama", "lm-studio", "deepseek");

    static String outputPortKey(OfficialApiSpec spec, OfficialApiSpec.Operation op) {
        if (op != null && op.stream != null) {
            String p = op.stream.port;
            if (p != null && !p.isBlank()) {
                return p;
            }
        }
        if (spec != null && spec.operations != null) {
            for (OfficialApiSpec.Operation other : spec.operations.values()) {
                if (other != null && other.stream != null) {
                    String p = other.stream.port;
                    if (p != null && !p.isBlank()) {
                        return p;
                    }
                }
            }
        }
        if (spec != null && spec.key != null && LLM_NODE_KEYS.contains(spec.key)) {
            return "llm_output";
        }
        return "output";
    }

    /** Pre-request validation failure: raised as a BPMN error for the error boundary event. */
    private static StudioQuackError invalidInput(String msg) {
        return new StudioQuackError("INVALID_INPUT", msg == null || msg.isBlank() ? "invalid input" : msg);
    }

    /**
     * Cheap assistant-shaped {@code result}: OpenAI {@code choices[0].message} when present,
     * otherwise the parsed body so PortSchemaFilter / PayloadUnwrap can walk it.
     */
    static Object unaryResult(Object parsed) {
        if (parsed instanceof Map<?, ?> m) {
            Object choices = m.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> c0) {
                    Object message = c0.get("message");
                    if (message != null) {
                        return message;
                    }
                }
            }
        }
        return parsed;
    }

    /** {@code output_files} / {@code outputFiles} on result or data; null when empty. */
    static List<?> outputFilesOf(Object result, Object data) {
        List<?> fromResult = filesFrom(result);
        if (fromResult != null && !fromResult.isEmpty()) {
            return fromResult;
        }
        List<?> fromData = filesFrom(data);
        return fromData != null && !fromData.isEmpty() ? fromData : null;
    }

    private static List<?> filesFrom(Object o) {
        if (!(o instanceof Map<?, ?> m)) {
            return null;
        }
        Object v = m.get("output_files");
        if (v == null) {
            v = m.get("outputFiles");
        }
        return v instanceof List<?> list ? list : null;
    }

    private boolean isIngress(OfficialApiSpec.Operation op) {
        if ("ingress".equals(op.kind)) {
            return true;
        }
        String n = op.name == null ? "" : op.name.toLowerCase();
        return INGRESS_NAMES.contains(n);
    }

    /**
     * When {@code stream} is non-null, {@code stream.path} replaces the operation path
     * (placeholders resolve the same way) and {@code stream.query} is merged into the URL.
     */
    String buildUrl(OfficialApiSpec.Operation op, Map<String, Object> in,
                    Map<String, Object> cfg, OfficialApiSpec.StreamBlock stream) {
        String base = op.baseUrl != null && !op.baseUrl.isBlank() ? op.baseUrl : spec.baseUrl;
        if (base == null || base.isBlank()) {
            throw new IllegalArgumentException("api_spec baseUrl is missing");
        }
        base = applyNewRelicDataRegionHosts(spec.key, base, cfg);
        base = resolvePostHogBaseUrl(spec.key, op, base);
        base = resolveTursoBaseUrl(spec.key, base, cfg);
        // baseUrl may carry {config} placeholders (e.g. https://{dc}.api.mailchimp.com/3.0);
        // host placeholders stay literal. Path placeholders are URL-encoded;
        // multi-segment keys keep '/' (see encodePathValue).
        base = substitute(base, in, cfg, spec.placeholderDefaults, false);
        String pathTemplate = op.path == null ? "" : op.path;
        if (stream != null && stream.path != null && !stream.path.isBlank()) {
            pathTemplate = stream.path;
        }
        pathTemplate = resolveTodoistListPath(op, in, cfg, pathTemplate);
        String path = substitute(pathTemplate, in, cfg, spec.placeholderDefaults, true);
        String url = base.endsWith("/") && path.startsWith("/")
                ? base.substring(0, base.length() - 1) + path
                : base + path;
        Map<String, String> query = new LinkedHashMap<>();
        for (String q : op.queryFrom) {
            String val = first(in, cfg, q);
            if (val != null) {
                query.put(op.queryName(q), val);
            }
        }
        if (stream != null && stream.query != null) {
            query.putAll(stream.query);
        }
        if ("query".equals(spec.authType)) {
            String secret = DataSupport.cfgStr(cfg, spec.authConfigKey, null);
            if (secret != null) {
                query.put(spec.authConfigKey, secret);
            }
        }
        String qs = DataSupport.query(query);
        if (qs.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + qs;
    }

    /** Package-visible for executor-decision tests — no HTTP. */
    StreamPlan planStream(OfficialApiSpec.Operation op, Map<String, Object> in, Map<String, Object> cfg) {
        boolean on = OfficialApiStream.enabled(op == null ? null : op.stream, in, Map.of(), cfg);
        if (!on || op == null || op.stream == null) {
            return new StreamPlan(false, null, null, null, null);
        }
        Map<String, String> headers = new LinkedHashMap<>();
        applyAuth(headers, cfg);
        headers.putAll(OfficialApiHeaders.interpolate(
                OfficialApiHeaders.merge(spec, op), in, cfg, Instant.now(clock)));
        headers.put("Accept", "text/event-stream");
        String url = buildUrl(op, in, cfg, op.stream);
        // Same config underlay as handle(): LLM bodies resolve model/knobs from config.
        Map<String, Object> merged = mergeBodyDefaults(op, underlayBodyConfig(op,
                OfficialApiStream.mergeInput(cfg, in), cfg, Map.of()));
        OfficialApiBody.Prepared prepared = prepareStreamBody(op, merged, 25);
        applySigV4(headers, op.method, url,
                prepared.bytes() == null ? new byte[0] : prepared.bytes(),
                cfg, in, Instant.now(clock));
        return new StreamPlan(true, url, headers,
                prepared.bytes() == null ? "" : new String(prepared.bytes(), StandardCharsets.UTF_8),
                op.stream.port);
    }

    record StreamPlan(boolean stream, String url, Map<String, String> headers, String body, String port) {
    }

    /** Replace {@code {var}} placeholders from input then config, then {@link OfficialApiSpec#placeholderDefaults}. */
    private static String substitute(String text, Map<String, Object> in,
                                     Map<String, Object> cfg, Map<String, String> placeholderDefaults, boolean encode) {
        Matcher m = PATH_VAR.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String key = m.group(1);
            String val = first(in, cfg, key);
            if (val == null && placeholderDefaults != null) {
                String d = placeholderDefaults.get(key);
                val = d == null || d.isBlank() ? null : d;
            }
            if (val == null) {
                throw new IllegalArgumentException("url parameter {" + key + "} is required");
            }
            m.appendReplacement(sb, Matcher.quoteReplacement(
                    encode ? encodePathValue(key, val) : val));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Currents GET /available/{catalog}: only these path segments exist on the official API. */
    private static final Set<String> CURRENTS_AVAILABLE_CATALOGS = Set.of("languages", "regions", "categories");

    /**
     * Docs use {@code kind}; some callers use {@code query} for search keywords. Normalize before URL build.
     */
    /**
     * Mixpanel ingest wants a JSON array. {@code event} plus {@code properties}
     * becomes one track event when {@code events} is omitted. Engage posts one
     * profile operation built from {@code distinct_id} and {@code properties}.
     */
    static Map<String, Object> mergeMixpanelInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                   Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"mixpanel".equalsIgnoreCase(nodeKey)
                || op == null || op.name == null) {
            return bodySrc;
        }
        return switch (op.name) {
            case "track", "import" -> mixpanelEvents(bodySrc);
            case "engage.set" -> mixpanelEngage(bodySrc, "$set");
            case "engage.unset" -> mixpanelEngage(bodySrc, "$unset");
            default -> bodySrc;
        };
    }

    /** Verbose ingest {@code status: 0} and a bare {@code 0} are rejections, not success. */
    static void rejectMixpanelIngestFailure(OfficialApiSpec.Operation op, Object data) {
        if (op == null || op.name == null) {
            return;
        }
        String name = op.name;
        if (!"track".equals(name) && !"import".equals(name) && !name.startsWith("engage.")) {
            return;
        }
        if (data instanceof Number num && num.intValue() == 0) {
            throw new StudioQuackError("REQUEST_FAILED", "Mixpanel rejected the ingest (status 0)");
        }
        if (data instanceof Map<?, ?> map && map.get("status") instanceof Number num && num.intValue() == 0) {
            Object err = map.get("error");
            String msg = err == null || String.valueOf(err).isBlank()
                    ? "Mixpanel rejected the ingest (status 0)"
                    : String.valueOf(err);
            throw new StudioQuackError("REQUEST_FAILED", msg);
        }
    }

    private static Map<String, Object> mixpanelEvents(Map<String, Object> bodySrc) {
        Object raw = mixpanelJson(bodySrc.get("events"));
        if (raw instanceof List<?> list && !list.isEmpty()) {
            List<Object> items = new ArrayList<>(list.size());
            for (Object item : list) {
                Object parsed = mixpanelJson(item);
                items.add(parsed instanceof Map<?, ?> row ? mixpanelTrackRow(row) : parsed);
            }
            Map<String, Object> out = new LinkedHashMap<>(bodySrc);
            out.put("events", items);
            return out;
        }
        String name = DataSupport.inStr(bodySrc, "event", null);
        if (name == null || name.isBlank()) {
            return bodySrc;
        }
        Map<String, Object> props = mixpanelObject(bodySrc.get("properties"));
        String distinct = DataSupport.inStr(bodySrc, "distinct_id", null);
        if (distinct != null && !distinct.isBlank() && !props.containsKey("distinct_id")) {
            props.put("distinct_id", distinct.strip());
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("event", name.strip());
        row.put("properties", props);
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("events", List.of(row));
        return out;
    }

    private static Map<String, Object> mixpanelTrackRow(Map<?, ?> row) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : row.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        Map<String, Object> props = mixpanelObject(out.get("properties"));
        moveInto(out, props, "distinct_id");
        moveInto(out, props, "token");
        out.put("properties", props);
        return out;
    }

    private static void moveInto(Map<String, Object> row, Map<String, Object> props, String key) {
        if (props.containsKey(key) || !row.containsKey(key) || row.get(key) == null) {
            return;
        }
        String value = String.valueOf(row.get(key)).strip();
        if (!value.isEmpty()) {
            props.put(key, value);
        }
    }

    private static Map<String, Object> mixpanelEngage(Map<String, Object> bodySrc, String opKey) {
        String distinct = DataSupport.inStr(bodySrc, "$distinct_id", null);
        if (distinct == null || distinct.isBlank()) {
            distinct = DataSupport.inStr(bodySrc, "distinct_id", null);
        }
        Map<String, Object> properties = mixpanelObject(bodySrc.get("properties"));
        String token = DataSupport.inStr(bodySrc, "$token", null);
        if (token == null || token.isBlank()) {
            Object fromProps = properties.get("token");
            token = fromProps == null ? null : String.valueOf(fromProps);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        if (token != null && !token.isBlank()) {
            row.put("$token", token.strip());
        }
        if (distinct != null && !distinct.isBlank()) {
            row.put("$distinct_id", distinct.strip());
        }
        if ("$unset".equals(opKey)) {
            row.put("$unset", mixpanelUnset(bodySrc.get("$unset"), properties));
        } else {
            Object set = mixpanelJson(bodySrc.get("$set"));
            Map<String, Object> setMap = set instanceof Map<?, ?> map && !map.isEmpty()
                    ? mixpanelObject(set) : properties;
            setMap.remove("token");
            setMap.remove("distinct_id");
            row.put("$set", setMap);
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("payload", List.of(row));
        return out;
    }

    private static List<String> mixpanelUnset(Object explicit, Map<String, Object> properties) {
        Object parsed = mixpanelJson(explicit);
        if (parsed instanceof List<?> list && !list.isEmpty()) {
            List<String> names = new ArrayList<>();
            for (Object item : list) {
                if (item != null && !String.valueOf(item).isBlank()) {
                    names.add(String.valueOf(item).strip());
                }
            }
            if (!names.isEmpty()) {
                return names;
            }
        }
        List<String> names = new ArrayList<>();
        for (String key : properties.keySet()) {
            if (key != null && !key.isBlank() && !"token".equals(key) && !"distinct_id".equals(key)) {
                names.add(key);
            }
        }
        return names;
    }

    private static Object mixpanelJson(Object value) {
        if (value instanceof String s) {
            String t = s.trim();
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    return Json.parse(t);
                } catch (RuntimeException ignored) {
                    return value;
                }
            }
        }
        return value;
    }

    private static Map<String, Object> mixpanelObject(Object value) {
        Object parsed = mixpanelJson(value);
        if (parsed instanceof Map<?, ?> map) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (var e : map.entrySet()) {
                if (e.getKey() != null) {
                    out.put(String.valueOf(e.getKey()), e.getValue());
                }
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    /**
     * Lemlist variable unsubscribe takes one path segment {@code value}. Callers
     * still send {@code email} or {@code domain}. Enrichment flags are query
     * parameters; the old body names are copied onto those query keys.
     */
    static Map<String, Object> mergeLemlistInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                  Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"lemlist".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        String name = op.name == null ? "" : op.name;
        Map<String, Object> out = bodySrc;
        if ("unsubscribe.add".equals(name) || "unsubscribe.delete".equals(name)) {
            if (blank(out, "value")) {
                String email = DataSupport.inStr(out, "email", null);
                String domain = DataSupport.inStr(out, "domain", null);
                String picked = email != null && !email.isBlank() ? email.strip()
                        : (domain != null && !domain.isBlank() ? domain.strip() : null);
                if (picked != null) {
                    out = new LinkedHashMap<>(out);
                    out.put("value", picked);
                }
            }
        }
        if ("enrichment.enrichPerson".equals(name) || "enrichment.enrichLead".equals(name)) {
            out = copyLemlistFlag(out, "findLinkedin", "linkedinEnrichment");
            out = copyLemlistFlag(out, "find_email", "findEmail");
            out = copyLemlistFlag(out, "find_phone", "findPhone");
            out = copyLemlistFlag(out, "linkedin_enrichment", "linkedinEnrichment");
        }
        return out;
    }

    /** Copy a legacy enrich flag onto the official query name when that name is blank. */
    private static Map<String, Object> copyLemlistFlag(Map<String, Object> in, String from, String to) {
        if (!blank(in, to) || blank(in, from)) {
            return in;
        }
        String raw = DataSupport.inStr(in, from, null);
        if (raw == null || raw.isBlank() || "false".equalsIgnoreCase(raw.strip()) || "0".equals(raw.strip())) {
            return in;
        }
        Map<String, Object> out = new LinkedHashMap<>(in);
        out.put(to, "true");
        return out;
    }

    static Map<String, Object> mergeCurrentsInputAliases(String nodeKey, Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"currents".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        String queryKw = DataSupport.inStr(out, "query", null);
        if (queryKw != null && !queryKw.isBlank() && blank(out, "keywords")) {
            out.put("keywords", queryKw);
        }
        if (blank(out, "catalog")) {
            String kind = DataSupport.inStr(out, "kind", null);
            if (kind != null && !kind.isBlank()) {
                out.put("catalog", kind.strip());
            }
        }
        return out;
    }

    /**
     * Reject mistaken catalog values before HTTP 404; normalize case to lowercase path segments.
     */
    void validateCurrentsAvailableCatalog(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"currents".equalsIgnoreCase(spec.key) || !"available".equals(op.name)) {
            return;
        }
        String raw = first(bodySrc, cfg, "catalog");
        if (raw == null || raw.isBlank()) {
            return;
        }
        String n = raw.strip().toLowerCase(Locale.ROOT);
        if (!CURRENTS_AVAILABLE_CATALOGS.contains(n)) {
            throw invalidInput("catalog must be one of languages, regions, or categories for operation available; got: "
                    + raw.strip());
        }
        if (bodySrc != null) {
            bodySrc.put("catalog", n);
        }
    }

    /** Provider sometimes returns the literal {@code "None"} for missing images — normalize to empty string. */
    static Object normalizeCurrentsNewsPayload(String nodeKey, Object parsed) {
        if (parsed == null || nodeKey == null || !"currents".equalsIgnoreCase(nodeKey) || !(parsed instanceof Map<?, ?> top)) {
            return parsed;
        }
        Object news = top.get("news");
        if (!(news instanceof List<?> list)) {
            return parsed;
        }
        List<Object> mapped = new ArrayList<>(list.size());
        for (Object item : list) {
            if (item instanceof Map<?, ?> row) {
                Map<String, Object> copy = new LinkedHashMap<>();
                for (var e : row.entrySet()) {
                    if (e.getKey() == null) {
                        continue;
                    }
                    String k = String.valueOf(e.getKey());
                    Object v = e.getValue();
                    if ("image".equals(k) && v instanceof String s) {
                        String t = s.trim();
                        if ("none".equalsIgnoreCase(t)) {
                            v = "";
                        }
                    }
                    copy.put(k, v);
                }
                mapped.add(copy);
            } else {
                mapped.add(item);
            }
        }
        Map<String, Object> out = new LinkedHashMap<>();
        top.forEach((k, v) -> {
            if (k == null) {
                return;
            }
            String key = String.valueOf(k);
            out.put(key, "news".equals(key) ? mapped : v);
        });
        return out;
    }

    /**
     * Validate that Codecov {@code {sha}} path parameters are 40-character hex commit ids
     * before issuing the HTTP request.
     */
    void validateCodecovSha(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"codecov".equalsIgnoreCase(spec.key)) {
            return;
        }
        String path = op.path == null ? "" : op.path;
        if (!path.contains("{sha}")) {
            return;
        }
        String sha = first(bodySrc, cfg, "sha");
        if (sha == null) {
            return;
        }
        String t = sha.strip();
        if (t.isEmpty()) {
            return;
        }
        if (!CODECOV_PATH_SHA.matcher(t).matches()) {
            throw invalidInput("sha must be a 40-character hexadecimal Git commit id for operation " + op.name);
        }
    }

    /**
     * Polygon {@code aggregates} embeds {@code from}/{@code to} in the path; malformed dates produce an
     * opaque upstream 404 — reject before HTTP (FA date-format gap).
     */
    void validatePolygonAggregateDates(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"polygon-io".equalsIgnoreCase(spec.key)) {
            return;
        }
        if (!"aggregates".equals(op.name)) {
            return;
        }
        for (String field : List.of("from", "to")) {
            String v = first(bodySrc, cfg, field);
            if (v == null) {
                continue;
            }
            String t = v.strip();
            if (t.isEmpty()) {
                continue;
            }
            if (!POLYGON_AGG_DATE.matcher(t).matches()) {
                throw invalidInput(field + " must be a YYYY-MM-DD date for operation " + op.name);
            }
        }
    }

    private static final Set<String> UNSTRUCTURED_STRATEGIES = Set.of(
            "auto", "vlm", "hi_res", "fast", "ocr_only");
    private static final Set<String> UNSTRUCTURED_CHUNKING = Set.of(
            "basic", "by_title", "by_page", "by_similarity");

    static Map<String, Object> mergeCalComInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                 Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"cal-com".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        if ("create-booking".equals(op.name)) {
            copyAliasIntoCanonical(out, "bookingFieldsResponses",
                    "booking_fields_responses", "bookingFieldResponses");
            normalizeCalComAttendeeField(out);
        }
        return out;
    }

    private static void normalizeCalComAttendeeField(Map<String, Object> bodySrc) {
        if (bodySrc == null) {
            return;
        }
        Object raw = bodySrc.get("attendee");
        if (!(raw instanceof String s) || s.isBlank()) {
            return;
        }
        String t = s.trim();
        if (!t.startsWith("{")) {
            return;
        }
        Map<String, Object> parsed = DataSupport.parseObject(t);
        if (parsed != null) {
            bodySrc.put("attendee", parsed);
        }
    }

    /**
     * Jina DeepSearch expects OpenAI message objects; tools often send {@code string[]} {@code messages}.
     * Embeddings use API field {@code input}; INPUT shortCode is {@code embed_input} so tool schemas
     * are not confused with the {@code input} envelope.
     */
    static Map<String, Object> mergeJinaInputs(String nodeKey, OfficialApiSpec.Operation op,
                                               Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"jina-ai".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(new LinkedHashMap<>(bodySrc));
        coerceCohereJsonListFields(out, "messages");
        if ("embed".equals(op.name)) {
            copyAliasIntoCanonical(out, "input", "embed_input", "text", "content", "query");
            if (blank(out, "input") && !blank(out, "texts")) {
                out.put("input", out.get("texts"));
            }
        }
        if ("deep_research".equals(op.name)) {
            normalizeJinaDeepResearchMessages(out);
        }
        return out;
    }

    private static void normalizeJinaDeepResearchMessages(Map<String, Object> out) {
        if (out == null) {
            return;
        }
        Object raw = out.get("messages");
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return;
        }
        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof String s && !s.isBlank()) {
                normalized.add(Map.of("role", "user", "content", s));
                continue;
            }
            if (item instanceof Map<?, ?> m) {
                Object role = m.get("role");
                Object content = m.get("content");
                if (content == null) {
                    content = m.get("text");
                }
                if (content != null && !String.valueOf(content).isBlank()) {
                    Map<String, Object> msg = new LinkedHashMap<>();
                    msg.put("role", role == null || String.valueOf(role).isBlank() ? "user" : String.valueOf(role));
                    msg.put("content", content);
                    normalized.add(msg);
                }
            }
        }
        if (!normalized.isEmpty()) {
            out.put("messages", normalized);
        }
    }

    void validateJinaInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                            Map<String, Object> cfg) {
        if (spec == null || !"jina-ai".equalsIgnoreCase(spec.key) || op == null) {
            return;
        }
        if (!"search".equals(op.name)) {
            return;
        }
        Object num = firstNonNull(bodySrc, cfg, "num");
        if (num == null) {
            return;
        }
        long n = numericLong(num);
        if (n <= 0) {
            throw invalidInput("num must be a positive integer for operation search");
        }
    }

    private static Object firstNonNull(Map<String, Object> bodySrc, Map<String, Object> cfg, String key) {
        if (bodySrc != null && bodySrc.containsKey(key) && bodySrc.get(key) != null) {
            return bodySrc.get(key);
        }
        if (cfg != null && cfg.containsKey(key) && cfg.get(key) != null) {
            return cfg.get(key);
        }
        return null;
    }

    private static long numericLong(Object num) {
        if (num instanceof Number n) {
            return n.longValue();
        }
        String s = String.valueOf(num).trim();
        if (s.isEmpty()) {
            return 0;
        }
        try {
            return Long.parseLong(s.split("\\.")[0]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static final Pattern US_CENSUS_VINTAGE = Pattern.compile("^\\d{4}$");

    void validateIntercomConfig(Map<String, Object> cfg) {
        if (spec == null || !"intercom".equalsIgnoreCase(spec.key)) {
            return;
        }
        String region = DataSupport.cfgStr(cfg, "region", null);
        if (region == null || region.isBlank()) {
            if (spec.placeholderDefaults != null) {
                region = spec.placeholderDefaults.get("region");
            }
        }
        if (region == null || region.isBlank()) {
            throw invalidInput(
                    "region is required in node config (api.intercom.io, api.eu.intercom.io, or api.au.intercom.io)");
        }
        if (region.contains("://") || region.contains("/") || region.contains("{")) {
            throw invalidInput("region must be the API host only (e.g. api.intercom.io)");
        }
    }

    static String enrichTransportError(String err, String url) {
        if (err == null || err.isBlank() || !err.startsWith("HTTP transport failed")) {
            return err;
        }
        String host = transportHostHint(url);
        if (host == null || host.isBlank()) {
            return err;
        }
        return err + " — check daemon egress/DNS/TLS to " + host;
    }

    private static String transportHostHint(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            return URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return "";
        }
    }

    void validateCustomerIoSearchLimit(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                       Map<String, Object> cfg) {
        if (spec == null || op == null || !"customer-io".equalsIgnoreCase(spec.key)
                || !"customer.search".equals(op.name)) {
            return;
        }
        Object raw = bodySrc == null ? null : bodySrc.get("limit");
        if (raw == null && cfg != null) {
            raw = cfg.get("limit");
        }
        if (raw == null) {
            return;
        }
        Long n = customerIoLimitValue(raw);
        if (n == null) {
            throw invalidInput("limit must be an integer from 0 to 1000 for operation customer.search");
        }
        if (n < 0 || n > 1000) {
            throw invalidInput("limit must be from 0 to 1000 for operation customer.search");
        }
    }

    private static Long customerIoLimitValue(Object raw) {
        if (raw instanceof Number num) {
            long v = num.longValue();
            if (num instanceof Double d && d != Math.rint(d)) {
                return null;
            }
            return v;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    void validateUsCensusInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                Map<String, Object> cfg) {
        if (spec == null || op == null || !"us-census-bureau".equalsIgnoreCase(spec.key)) {
            return;
        }
        String vintage = first(bodySrc, cfg, "vintage");
        if (vintage != null && !vintage.isBlank() && !US_CENSUS_VINTAGE.matcher(vintage.strip()).matches()) {
            throw invalidInput("vintage must be a four-digit year, e.g. 2010");
        }
        String dataset = first(bodySrc, cfg, "dataset");
        if (dataset != null && !dataset.isBlank() && !dataset.contains("/")) {
            throw invalidInput(
                    "dataset must be program/dataset (two path segments), e.g. dec/sf1 or acs/acs5 — not a collection name alone");
        }
    }

    void validateAtlassianBasicEmailConfig(Map<String, Object> cfg) {
        if (spec == null || !"basic".equalsIgnoreCase(spec.authType)) {
            return;
        }
        String key = spec.key == null ? "" : spec.key.toLowerCase(Locale.ROOT);
        if (!"bitbucket".equals(key) && !"jira".equals(key) && !"confluence".equals(key)) {
            return;
        }
        String email = DataSupport.cfgStr(cfg, spec.authUserKey, null);
        if (email == null || email.isBlank()) {
            throw invalidInput(
                    "email is required in node config (Atlassian account email for API token Basic auth)");
        }
    }

    /**
     * GitLab: agents often send {@code content} instead of {@code body}/{@code description}, or put
     * branch/tag names in {@code content}.
     */
    static Map<String, Object> mergeGitlabInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                 Map<String, Object> bodySrc) {
        if (nodeKey == null || !"gitlab".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = bodySrc == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bodySrc);
        if ("issue_create".equals(op.name) || "issue_update".equals(op.name)) {
            if (blank(out, "description") && !blank(out, "content")) {
                out.put("description", DataSupport.inStr(out, "content", null));
            }
            if ("issue_create".equals(op.name) && blank(out, "title")) {
                copyAliasIntoCanonical(out, "title", "name");
            }
        }
        if ("issue_note".equals(op.name) && blank(out, "body") && !blank(out, "content")) {
            out.put("body", DataSupport.inStr(out, "content", null));
        }
        if ("branch_create".equals(op.name) && blank(out, "branch") && !blank(out, "content")) {
            out.put("branch", DataSupport.inStr(out, "content", null));
        }
        if ("tag_create".equals(op.name) && blank(out, "tag_name")) {
            if (!blank(out, "content")) {
                out.put("tag_name", DataSupport.inStr(out, "content", null));
            } else if (!blank(out, "name")) {
                out.put("tag_name", DataSupport.inStr(out, "name", null));
            }
        }
        if ("release_create".equals(op.name) && blank(out, "tag_name") && !blank(out, "ref")) {
            String ref = DataSupport.inStr(out, "ref", null);
            if (ref != null && !ref.isBlank() && !ref.equalsIgnoreCase("main") && !ref.equalsIgnoreCase("master")) {
                out.put("tag_name", ref);
            }
        }
        if ("mr_create".equals(op.name) && blank(out, "title") && !blank(out, "content")) {
            out.put("title", DataSupport.inStr(out, "content", null));
        }
        return out;
    }

    static Map<String, Object> mergeJiraInputs(String nodeKey, OfficialApiSpec.Operation op,
                                               Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"jira".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(new LinkedHashMap<>(bodySrc));
        if ("issue_create".equals(op.name) || "issue_update".equals(op.name)) {
            copyAliasIntoCanonical(out, "summary", "subject");
            Object fields = out.get("fields");
            if (fields instanceof Map<?, ?> fm) {
                pullJiraMappedField(out, fm, "summary");
                pullJiraMappedField(out, fm, "project_key", "project", "key");
                pullJiraMappedField(out, fm, "issuetype");
            }
        }
        if (op.name != null && op.name.startsWith("comment_")) {
            if (blank(out, "body")) {
                Object text = firstNonBlankObject(out, "textBody", "text", "comment");
                if (text instanceof Map<?, ?>) {
                    out.put("body", text);
                } else if (text instanceof String s && !s.isBlank()) {
                    out.put("body", jiraAdfPlainText(s));
                }
            }
        }
        return out;
    }

    private static void pullJiraMappedField(Map<String, Object> out, Map<?, ?> fields, String flatKey,
                                            String... nestedPath) {
        if (!blank(out, flatKey) || nestedPath.length == 0) {
            return;
        }
        Object cur = fields;
        for (String seg : nestedPath) {
            if (!(cur instanceof Map<?, ?> m)) {
                return;
            }
            cur = m.get(seg);
        }
        if (cur != null && !String.valueOf(cur).isBlank()) {
            out.put(flatKey, cur);
        }
    }

    private static void pullJiraMappedField(Map<String, Object> out, Map<?, ?> fields, String flatKey) {
        pullJiraMappedField(out, fields, flatKey, flatKey);
    }

    private static Object firstNonBlankObject(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return null;
        }
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s) {
                if (!s.isBlank()) {
                    return s;
                }
            } else if (v != null) {
                return v;
            }
        }
        return null;
    }

    private static Map<String, Object> jiraAdfPlainText(String text) {
        return Map.of(
                "type", "doc",
                "version", 1,
                "content", List.of(Map.of(
                        "type", "paragraph",
                        "content", List.of(Map.of("type", "text", "text", text)))));
    }

    static String clarifyAtlassianCloudHttpStatusMessage(String msg, int httpStatus) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        int colon = msg.indexOf(": ");
        if (colon < 0) {
            if (msg.startsWith("HTTP " + httpStatus)) {
                return "HTTP " + httpStatus + ": "
                        + DataSupport.clarifyAtlassianCloudHttpDetail("", httpStatus);
            }
            return DataSupport.clarifyAtlassianCloudHttpDetail(msg, httpStatus);
        }
        String prefix = msg.substring(0, colon + 2);
        String detail = msg.substring(colon + 2);
        String clarified = DataSupport.clarifyAtlassianCloudHttpDetail(detail, httpStatus);
        return clarified.equals(detail) ? msg : prefix + clarified;
    }

    static String clarifyInvoiceNinjaHttpStatusMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        int colon = msg.indexOf(": ");
        if (colon < 0) {
            return DataSupport.clarifyInvoiceNinjaTierDetail(msg);
        }
        String prefix = msg.substring(0, colon + 2);
        String detail = msg.substring(colon + 2);
        String clarified = DataSupport.clarifyInvoiceNinjaTierDetail(detail);
        return clarified.equals(detail) ? msg : prefix + clarified;
    }

    static String clarifyBitbucketHttpStatusMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        int colon = msg.indexOf(": ");
        if (colon < 0) {
            return DataSupport.clarifyBitbucketAuthDetail(msg);
        }
        String prefix = msg.substring(0, colon + 2);
        String detail = msg.substring(colon + 2);
        String clarified = DataSupport.clarifyBitbucketAuthDetail(detail);
        return clarified.equals(detail) ? msg : prefix + clarified;
    }

    static String clarifyCalComHttpStatusMessage(String msg) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        int colon = msg.indexOf(": ");
        if (colon < 0) {
            return clarifyCalComAuthOrBookingDetail(msg);
        }
        String prefix = msg.substring(0, colon + 2);
        String detail = msg.substring(colon + 2);
        String clarified = clarifyCalComAuthOrBookingDetail(detail);
        return clarified.equals(detail) ? msg : prefix + clarified;
    }

    private static String clarifyCalComAuthOrBookingDetail(String detail) {
        if (detail == null || detail.isBlank()) {
            return detail;
        }
        String lower = detail.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("invalid access token") || lower.contains("oauth client")) {
            return detail.trim()
                    + " — set a valid Cal.com API v2 key in configurationParameters api_key "
                    + "(https://cal.com/docs/api-reference/v2/introduction)";
        }
        return DataSupport.clarifyCalComBookingFieldDetail(detail);
    }

    void validateCalComApiKey(Map<String, Object> cfg) {
        if (spec == null || !"cal-com".equalsIgnoreCase(spec.key)) {
            return;
        }
        String key = cfg == null ? null : DataSupport.cfgStr(cfg, "api_key", null);
        if (key == null || key.isBlank()) {
            throw invalidInput(
                    "api_key is required in configurationParameters (Cal.com API v2 Bearer token)");
        }
    }

    void rejectCalComHollowBookingResponse(OfficialApiSpec.Operation op, DataSupport.HttpResp resp,
                                           Object data) {
        if (op == null || resp == null) {
            return;
        }
        if (!List.of("reschedule-booking", "cancel-booking", "get-booking").contains(op.name)) {
            return;
        }
        String body = resp.body() == null ? "" : resp.body().strip();
        if (body.isEmpty()) {
            throw new StudioQuackError(
                    "REQUEST_FAILED",
                    "Cal.com returned HTTP " + resp.status() + " with an empty body for operation " + op.name,
                    Map.of("http_status", resp.status()));
        }
        if (data instanceof Map<?, ?> map && map.isEmpty()) {
            throw new StudioQuackError(
                    "REQUEST_FAILED",
                    "Cal.com returned an empty JSON object for operation " + op.name
                            + " — expected a booking payload (check uid and api_key)",
                    Map.of("http_status", resp.status()));
        }
    }

    void validateCalComBookingMutations(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                        Map<String, Object> cfg) {
        if (spec == null || op == null || !"cal-com".equalsIgnoreCase(spec.key)) {
            return;
        }
        if ("reschedule-booking".equals(op.name) && blank(bodySrc, "start") && blank(cfg, "start")) {
            throw invalidInput("start is required for operation reschedule-booking");
        }
    }

    void validateCalComCreateBooking(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                     Map<String, Object> cfg) {
        if (spec == null || op == null || !"cal-com".equalsIgnoreCase(spec.key)
                || !"create-booking".equals(op.name)) {
            return;
        }
        if (blank(bodySrc, "start") && blank(cfg, "start")) {
            throw invalidInput("start is required for operation create-booking");
        }
        Object attendeeRaw = bodySrc == null ? null : bodySrc.get("attendee");
        if (attendeeRaw == null && cfg != null) {
            attendeeRaw = cfg.get("attendee");
        }
        if (attendeeRaw == null || (attendeeRaw instanceof String s && s.isBlank())) {
            throw invalidInput("attendee is required for operation create-booking");
        }
        if (attendeeRaw instanceof String s) {
            Map<String, Object> parsed = DataSupport.parseObject(s.trim());
            if (parsed == null) {
                throw invalidInput(
                        "attendee must be a JSON object (name, timeZone, email, …), not a plain string");
            }
        } else if (!(attendeeRaw instanceof Map<?, ?>)) {
            throw invalidInput("attendee must be a JSON object for operation create-booking");
        }
        long eventTypeId = calComEventTypeId(bodySrc, cfg);
        String slug = first(bodySrc, cfg, "eventTypeSlug");
        String username = first(bodySrc, cfg, "username");
        String teamSlug = first(bodySrc, cfg, "teamSlug");
        boolean hasId = eventTypeId > 0;
        boolean hasSlug = slug != null && !slug.isBlank()
                && ((username != null && !username.isBlank()) || (teamSlug != null && !teamSlug.isBlank()));
        if (!hasId && !hasSlug) {
            throw invalidInput(
                    "create-booking requires eventTypeId, or eventTypeSlug with username or teamSlug");
        }
    }

    private static long calComEventTypeId(Map<String, Object> bodySrc, Map<String, Object> cfg) {
        Object raw = bodySrc == null ? null : bodySrc.get("eventTypeId");
        if (raw == null && cfg != null) {
            raw = cfg.get("eventTypeId");
        }
        if (raw instanceof Number n) {
            return n.longValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
        return 0L;
    }

    void validateUnstructuredInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                    Map<String, Object> cfg) {
        if (spec == null || op == null || !"unstructured".equalsIgnoreCase(spec.key)) {
            return;
        }
        String strategy = first(bodySrc, cfg, "strategy");
        if (strategy != null && !strategy.isBlank()) {
            String norm = strategy.trim().toLowerCase(Locale.ROOT);
            if (!UNSTRUCTURED_STRATEGIES.contains(norm)) {
                throw invalidInput("strategy must be one of: auto, vlm, hi_res, fast, ocr_only");
            }
        }
        String chunking = first(bodySrc, cfg, "chunking_strategy");
        if (chunking != null && !chunking.isBlank()) {
            String norm = chunking.trim().toLowerCase(Locale.ROOT);
            if (!UNSTRUCTURED_CHUNKING.contains(norm)) {
                throw invalidInput(
                        "chunking_strategy must be one of: basic, by_title, by_page, by_similarity");
            }
        }
        Object files = bodySrc == null ? null : bodySrc.get("files");
        if (files == null && cfg != null) {
            files = cfg.get("files");
        }
        if (files instanceof String s && s.contains(",")) {
            throw invalidInput("files accepts one file per request");
        }
    }

    static Map<String, Object> mergeVimeoInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"vimeo".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        if ("video_get".equals(op.name)) {
            Object v = out.get("video_id");
            if (v != null) {
                String normalized = normalizeVimeoVideoId(String.valueOf(v));
                if (normalized != null) {
                    out.put("video_id", normalized);
                }
            }
        }
        return out;
    }

    static String normalizeVimeoVideoId(String raw) {
        if (raw == null) {
            return null;
        }
        String s = raw.strip();
        if (s.isEmpty()) {
            return s;
        }
        int tail = s.lastIndexOf("/videos/");
        if (tail >= 0) {
            s = s.substring(tail + "/videos/".length());
        } else if (s.startsWith("videos/")) {
            s = s.substring("videos/".length());
        }
        int slash = s.indexOf('/');
        if (slash >= 0) {
            s = s.substring(0, slash);
        }
        return s.strip();
    }

    static Map<String, Object> mergeEmeliaInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                 Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"emelia".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        if ("campaign.create".equals(op.name)) {
            if (blank(out, "name")) {
                for (String alias : List.of("campaignName", "title", "campaign_name")) {
                    if (!blank(out, alias)) {
                        out.put("name", out.get(alias));
                        break;
                    }
                }
            }
        }
        if ("contactList.list".equals(op.name)) {
            if (blank(out, "page")) {
                out.put("page", 1);
            }
            if (blank(out, "limit")) {
                out.put("limit", 50);
            }
        }
        return out;
    }

    void validateEmeliaInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"emelia".equalsIgnoreCase(spec.key)) {
            return;
        }
        if ("campaign.create".equals(op.name)) {
            String name = first(bodySrc, cfg, "name");
            if (name != null && name.isBlank()) {
                throw invalidInput("name must be non-empty for operation campaign.create");
            }
        }
        String path = op.path == null ? "" : op.path;
        if (path.contains("{campaignId}")) {
            String id = first(bodySrc, cfg, "campaignId");
            if (id != null && !id.isBlank() && !EMELIA_OBJECT_ID.matcher(id.strip()).matches()) {
                throw invalidInput(
                        "campaignId must be a 24-character hex MongoDB ObjectId for operation " + op.name);
            }
        }
        if (path.contains("{listId}")) {
            String id = first(bodySrc, cfg, "listId");
            if (id != null && !id.isBlank() && !EMELIA_OBJECT_ID.matcher(id.strip()).matches()) {
                throw invalidInput("listId must be a 24-character hex MongoDB ObjectId for operation " + op.name);
            }
        }
    }

    void validateVimeoInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"vimeo".equalsIgnoreCase(spec.key)) {
            return;
        }
        if ("video_list".equals(op.name) || "folder_list".equals(op.name)) {
            validateVimeoPerPage(op, bodySrc, cfg);
            validateVimeoPage(op, bodySrc, cfg);
        }
        if ("video_get".equals(op.name)) {
            String id = first(bodySrc, cfg, "video_id");
            if (id != null && !id.isBlank() && !VIMEO_VIDEO_ID.matcher(id.strip()).matches()) {
                throw invalidInput("video_id must be a numeric Vimeo clip id");
            }
        }
    }

    private void validateVimeoPerPage(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        String pp = first(bodySrc, cfg, "per_page");
        if (pp == null || pp.isBlank()) {
            return;
        }
        try {
            int n = Integer.parseInt(pp.strip());
            if (n < 1 || n > 100) {
                throw invalidInput("per_page must be between 1 and 100 for operation " + op.name);
            }
        } catch (NumberFormatException e) {
            throw invalidInput("per_page must be an integer for operation " + op.name);
        }
    }

    private void validateVimeoPage(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        String page = first(bodySrc, cfg, "page");
        if (page == null || page.isBlank()) {
            return;
        }
        try {
            int p = Integer.parseInt(page.strip());
            if (p < 1) {
                throw invalidInput("page must be at least 1 for operation " + op.name);
            }
            if (p > VIMEO_MAX_PAGE) {
                throw invalidInput("page is out of range (max " + VIMEO_MAX_PAGE + ") for operation " + op.name);
            }
        } catch (NumberFormatException e) {
            throw invalidInput("page must be an integer for operation " + op.name);
        }
    }

    void validateSurveyMonkeySurveyId(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                    Map<String, Object> cfg) {
        if (spec == null || op == null || !"surveymonkey".equalsIgnoreCase(spec.key)) {
            return;
        }
        String path = op.path == null ? "" : op.path;
        if (!path.contains("{survey_id}")) {
            return;
        }
        String id = first(bodySrc, cfg, "survey_id");
        if (id == null) {
            return;
        }
        String t = id.strip();
        if (t.isEmpty()) {
            return;
        }
        if (!SURVEY_MONKEY_SURVEY_ID.matcher(t).matches()) {
            throw invalidInput("'" + t + "' does not match '^[1-9][0-9]*$'");
        }
    }

    /**
     * Tool-call aliases for CloudConvert ({@code job_id} → {@code id}, {@code link} → {@code url}) so
     * AI tool payloads match the INPUT shortCodes.
     */
    static Map<String, Object> mergeCloudConvertAliases(String nodeKey, Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"cloudconvert".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        cloudConvertPlainFileStringToId(out);
        cloudConvertNameToCaptureUrl(out);
        return out;
    }

    /** Agents often send job/task UUIDs in {@code file} when the tool schema omits {@code id}. */
    private static void cloudConvertPlainFileStringToId(Map<String, Object> out) {
        if (out == null || !blank(out, "id")) {
            return;
        }
        Object file = out.get("file");
        if (!(file instanceof String s)) {
            return;
        }
        String t = s.trim();
        if (t.isEmpty() || t.startsWith("sq-file") || t.startsWith("sq-ref:")) {
            return;
        }
        out.put("id", t);
    }

    private static void cloudConvertNameToCaptureUrl(Map<String, Object> out) {
        if (out == null || !blank(out, "url")) {
            return;
        }
        Object op = out.get("operation");
        if (op == null || !"capture_website".equalsIgnoreCase(String.valueOf(op).trim())) {
            return;
        }
        Object name = out.get("name");
        if (!(name instanceof String s)) {
            return;
        }
        String t = s.trim();
        if (t.startsWith("http://") || t.startsWith("https://")) {
            out.put("url", t);
        }
    }

    /**
     * LLM function-call shims often nest parameters under {@code args} / {@code arguments}.
     * Unwrap once so path params and {@code required} checks see the same keys as studio INPUT.
     */
    public static Map<String, Object> mergeToolCallArgs(Map<String, Object> in) {
        if (in == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>(in);
        Object nested = unwrapToolArgsPayload(out.remove("args"));
        if (nested == null) {
            nested = unwrapToolArgsPayload(out.remove("arguments"));
        }
        if (nested instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (e.getKey() == null) {
                    continue;
                }
                String k = String.valueOf(e.getKey());
                if (!out.containsKey(k)) {
                    out.put(k, e.getValue());
                }
            }
        }
        return out;
    }

    private static Object unwrapToolArgsPayload(Object v) {
        if (v == null) {
            return null;
        }
        if (v instanceof Map<?, ?> m) {
            return m;
        }
        if (v instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            if (t.startsWith("{") || t.startsWith("[")) {
                try {
                    return Json.parse(t);
                } catch (RuntimeException e) {
                    return null;
                }
            }
        }
        return null;
    }

    /**
     * LLM tools and subprocess bridges often nest the real payload under {@code input} or {@code request}.
     */
    public static Map<String, Object> flattenNestedToolInput(Map<String, Object> in) {
        if (in == null) {
            return new LinkedHashMap<>();
        }
        Map<String, Object> out = new LinkedHashMap<>(in);
        for (String envelope : List.of(
                "input", "request", "params", "arguments", "args", "payload", "data", "body",
                "parameters", "fields", "variables", "address", "shippo", "shippo_args",
                "shippo_request", "tool_input", "operationParams", "params_json", "config",
                "configuration")) {
            Object nested = out.get(envelope);
            if (!(nested instanceof Map<?, ?> wrap)) {
                continue;
            }
            wrap.forEach((k, v) -> {
                if (k == null || v == null) {
                    return;
                }
                String key = String.valueOf(k);
                if (!out.containsKey(key)) {
                    out.put(key, v);
                }
            });
        }
        unwrapValueWrapperFields(out, "id", "name", "operation", "query");
        return out;
    }

    static void unwrapValueWrapperFields(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof Map<?, ?> wrap) {
                Object inner = wrap.get("value");
                if (inner == null) {
                    inner = wrap.get("Value");
                }
                if (inner != null) {
                    m.put(key, inner);
                }
            } else if (v instanceof Number n) {
                m.put(key, DataSupport.integralText(n));
            }
        }
    }

    /** Path {@code {id}} ops: tools often send mailbox_id, parser_id, or document_id. */
    static Map<String, Object> mergeCommonPathIdAliases(Map<String, Object> bodySrc) {
        if (bodySrc == null || bodySrc.isEmpty()) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        copyAliasIntoCanonical(out, "id", "mailbox_id", "parser_id", "document_id", "mailboxId",
                "parserId", "documentId", "ID", "bounceID", "bounce_id", "bounceId",
                "job_id", "task_id", "uuid", "file_id", "jobId", "taskId", "fileId");
        copyAliasIntoCanonical(out, "name", "mailbox_name", "parser_name", "mailboxName", "parserName");
        copyAliasIntoCanonical(out, "url", "link", "website", "page_url", "source_url");
        return out;
    }

    /**
     * Cross-node agent shims: JSON-encoded arrays/objects, singular {@code label} → {@code labels},
     * SQL field aliases when the op lists those keys in {@code bodyFrom}, and {@code idLabel}
     * → {@code value} when declared on query or body.
     */
    static Map<String, Object> mergeCommonAgentPayloadFields(OfficialApiSpec.Operation op,
                                                             Map<String, Object> bodySrc) {
        if (bodySrc == null || bodySrc.isEmpty()) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        coerceJsonTextFields(out, "requests", "messages", "documents", "texts", "inputs", "tools",
                "events", "fields", "parameters", "statements", "ids", "paper_ids", "labels",
                "args", "named_args", "record");
        boolean wantsLabels = op != null && op.bodyFrom != null && op.bodyFrom.contains("labels");
        boolean wantsSql = op != null && op.bodyFrom != null && op.bodyFrom.contains("sql");
        boolean wantsValueOnBody = op != null && op.bodyFrom != null && op.bodyFrom.contains("value");
        boolean wantsValueOnQuery = op != null && op.queryFrom != null && op.queryFrom.contains("value");
        if (wantsLabels && !blank(out, "label") && blank(out, "labels")) {
            String label = DataSupport.inStr(out, "label", null);
            if (label != null && !label.isBlank()) {
                out.put("labels", List.of(label.strip()));
            }
        }
        if (wantsSql && blank(out, "sql")) {
            for (String alias : List.of("query", "statement", "command", "script", "code", "stmt")) {
                String s = DataSupport.inStr(out, alias, null);
                if (s != null && !s.isBlank()) {
                    out.put("sql", s);
                    break;
                }
            }
        }
        if ((wantsValueOnBody || wantsValueOnQuery) && blank(out, "value") && !blank(out, "idLabel")) {
            out.put("value", DataSupport.inStr(out, "idLabel", null));
        }
        mergeCommonOAuthFormFieldAliases(op, out);
        applyBracketParameterAliases(out, op);
        mergeEmbedInputFieldAliases(op, out);
        return out;
    }

    /** OAuth token ops: agents often send camelCase grant/client fields. */
    static void mergeCommonOAuthFormFieldAliases(OfficialApiSpec.Operation op, Map<String, Object> out) {
        if (op == null || out == null || out.isEmpty()) {
            return;
        }
        boolean onBody = op.bodyFrom != null;
        boolean onQuery = op.queryFrom != null;
        if (onBody && op.bodyFrom.contains("grant_type")
                || onQuery && op.queryFrom.contains("grant_type")) {
            copyAliasIntoCanonical(out, "grant_type", "grantType", "grant-type", "GrantType");
        }
        if (onBody && op.bodyFrom.contains("client_id")) {
            copyAliasIntoCanonical(out, "client_id", "clientId", "ClientId");
        }
        if (onBody && op.bodyFrom.contains("client_secret")) {
            copyAliasIntoCanonical(out, "client_secret", "clientSecret", "ClientSecret");
        }
        if (onBody && op.bodyFrom.contains("refresh_token")) {
            copyAliasIntoCanonical(out, "refresh_token", "refreshToken", "RefreshToken");
        }
    }

    /**
     * INPUT shortCodes like {@code filter[name]} are awkward in tool JSON schemas; agents send
     * {@code filter_name} instead.
     */
    public static String bracketFieldAlias(String bracketKey) {
        if (bracketKey == null || bracketKey.isBlank() || !bracketKey.contains("[")
                || !bracketKey.contains("]")) {
            return bracketKey == null ? "" : bracketKey;
        }
        String alias = bracketKey.replace("]", "").replace("[", "_");
        while (alias.endsWith("_")) {
            alias = alias.substring(0, alias.length() - 1);
        }
        return alias;
    }

    static void applyBracketParameterAliases(Map<String, Object> out) {
        applyBracketParameterAliases(out, null);
    }

    static void applyBracketParameterAliases(Map<String, Object> out, OfficialApiSpec.Operation op) {
        if (out == null || out.isEmpty()) {
            return;
        }
        List<String> bracketKeys = new ArrayList<>();
        if (op != null) {
            if (op.queryFrom != null) {
                bracketKeys.addAll(op.queryFrom);
            }
            if (op.bodyFrom != null) {
                bracketKeys.addAll(op.bodyFrom);
            }
        }
        for (String key : List.copyOf(out.keySet())) {
            if (key != null && key.contains("[") && key.contains("]")) {
                bracketKeys.add(key);
            }
        }
        for (String bracket : bracketKeys) {
            if (bracket == null || !bracket.contains("[") || !bracket.contains("]")) {
                continue;
            }
            String alias = bracketFieldAlias(bracket);
            if (alias.isBlank() || alias.equals(bracket)) {
                continue;
            }
            if (!blank(out, alias) && blank(out, bracket)) {
                out.put(bracket, out.get(alias));
            }
        }
    }

    /** Avoid tool JSON schema {@code input} clashing with the {@code input} envelope on embed ops. */
    private static void mergeEmbedInputFieldAliases(OfficialApiSpec.Operation op, Map<String, Object> out) {
        if (op == null || out == null || op.bodyFrom == null || !op.bodyFrom.contains("input")) {
            return;
        }
        if (op.bodyFrom.contains("texts")) {
            return;
        }
        copyAliasIntoCanonical(out, "input", "embed_input", "text");
    }

    static void coerceJsonTextFields(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof String s) {
                Object parsed = unwrapToolArgsPayload(s);
                if (parsed != null && !(parsed instanceof String)) {
                    m.put(key, parsed);
                }
            }
        }
    }

    static Map<String, Object> mergeNewRelicInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                   Map<String, Object> bodySrc) {
        if (bodySrc == null || nodeKey == null || !"new-relic".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(new LinkedHashMap<>(bodySrc));
        if ("incident_get".equals(op.name)) {
            copyAliasIntoCanonical(out, "incident_id", "incidentId", "incident-id");
        }
        copyAliasIntoCanonical(out, "accountId", "account_id", "accountID");
        copyAliasIntoCanonical(out, "app_id", "appId", "application_id");
        return out;
    }

    static String clarifyNewRelicHttpStatusMessage(String msg, int httpStatus) {
        if (msg == null || msg.isBlank()) {
            return msg;
        }
        int colon = msg.indexOf(": ");
        if (colon < 0) {
            if (msg.startsWith("HTTP " + httpStatus)) {
                return "HTTP " + httpStatus + ": "
                        + DataSupport.clarifyNewRelicAuthDetail("", httpStatus);
            }
            return DataSupport.clarifyNewRelicAuthDetail(msg, httpStatus);
        }
        String prefix = msg.substring(0, colon + 2);
        String detail = msg.substring(colon + 2);
        String clarified = DataSupport.clarifyNewRelicAuthDetail(detail, httpStatus);
        return clarified.equals(detail) ? msg : prefix + clarified;
    }

    /**
     * Event API ingest: agents often nest fields under {@code input}; coerce {@code events} from JSON text
     * or a single event object.
     */
    static Map<String, Object> mergeNewRelicEventIngest(String nodeKey, OfficialApiSpec.Operation op,
                                                        Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"new-relic".equalsIgnoreCase(nodeKey)
                || !"event_ingest".equals(op.name)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        Object events = out.get("events");
        if (events == null) {
            events = out.remove("events_json");
        }
        if (events == null) {
            events = out.get("event");
            if (events != null) {
                out.remove("event");
            }
        }
        if (events instanceof String s) {
            Object parsed = unwrapToolArgsPayload(s);
            if (parsed != null) {
                events = parsed;
            }
        }
        if (events instanceof Map<?, ?> one) {
            events = List.of(one);
        }
        if (events != null) {
            out.put("events", events);
        }
        return out;
    }

    /**
     * Postmark: {@code list-bounces} needs {@code count}/{@code offset} query params; agents send
     * casing variants. {@code send-batch} wire body is a JSON array (not {@code {Messages:…}}).
     * Template sends sometimes nest fields under {@code Request}.
     */
    /**
     * Todoist API v1: singular {@code label} on create/update maps to {@code labels}. {@code filter} on
     * {@code list} uses {@code GET /tasks/filter} with wire param {@code query} (see queryMap).
     */
    /**
     * Turso libSQL v2: build {@code {requests:[execute,close]}} from top-level {@code sql}, a
     * pipeline array (or JSON string), or common agent shapes; map sql/query/statement aliases.
     */
    static Map<String, Object> mergeTursoInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (nodeKey == null || !"turso".equalsIgnoreCase(nodeKey) || op == null
                || !"execute.sql".equals(op.name)) {
            return bodySrc;
        }
        Map<String, Object> out = bodySrc == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bodySrc);
        String sql = tursoSqlText(out, cfg);
        List<Object> pipeline = tursoNormalizePipeline(out.get("requests"), sql, cfg, out);
        if (pipeline == null || pipeline.isEmpty()) {
            return out;
        }
        tursoEnsureCloseRequest(pipeline);
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("requests", pipeline);
        return wire;
    }

    void validateTursoInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                             Map<String, Object> cfg) {
        if (spec == null || !"turso".equalsIgnoreCase(spec.key) || op == null
                || !"execute.sql".equals(op.name)) {
            return;
        }
        List<?> pipeline = bodySrc == null ? null : (bodySrc.get("requests") instanceof List<?> l ? l : null);
        if (tursoPipelineHasSql(pipeline)) {
            return;
        }
        throw invalidInput(
                "sql is required for operation execute.sql — set INPUT sql (string) or requests "
                        + "(array of pipeline steps with stmt.sql)");
    }

    private static final java.util.regex.Pattern TWILIO_E164 =
            java.util.regex.Pattern.compile("\\+[1-9]\\d{7,14}");
    private static final java.util.regex.Pattern TWILIO_WHATSAPP_ADDR =
            java.util.regex.Pattern.compile("whatsapp:\\+[1-9]\\d{7,14}", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern TWILIO_ACCOUNT_SID =
            java.util.regex.Pattern.compile("^AC[0-9a-fA-F]{32}$");
    private static final java.util.regex.Pattern TWILIO_HTTP_URL =
            java.util.regex.Pattern.compile("^https?://\\S+", java.util.regex.Pattern.CASE_INSENSITIVE);

    static Map<String, Object> mergeTwilioConfig(Map<String, Object> cfg) {
        if (cfg == null) {
            return Map.of();
        }
        Map<String, Object> out = new LinkedHashMap<>(cfg);
        copyAliasIntoCanonical(out, "account_sid", "accountSid", "AccountSid", "sid");
        return out;
    }

    void validateTwilioInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                              Map<String, Object> cfg) {
        if (spec == null || !"twilio".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String sid = first(bodySrc, cfg, "account_sid");
        if (sid == null || sid.isBlank()) {
            throw invalidInput(
                    "account_sid is required in configurationParameters (Twilio Account SID, starts with AC)");
        }
        if (!TWILIO_ACCOUNT_SID.matcher(sid.strip()).matches()) {
            throw invalidInput("account_sid must be a Twilio Account SID (format AC followed by 32 hex digits)");
        }
        String to = first(bodySrc, cfg, "To");
        if (to != null && !to.isBlank()) {
            if (!twilioToAccepted(to.strip(), op.name)) {
                throw invalidInput(
                        "To must be E.164 (+15551234567) or whatsapp:+E.164 for operation " + op.name);
            }
        } else if (requiresTwilioTo(op.name)) {
            // required[] already checked; skip duplicate
        }
        if ("send-whatsapp".equals(op.name)) {
            String from = first(bodySrc, cfg, "From");
            if (from != null && !from.isBlank()
                    && !TWILIO_WHATSAPP_ADDR.matcher(from.strip()).matches()) {
                throw invalidInput(
                        "From must be whatsapp:+E.164 for operation send-whatsapp (e.g. whatsapp:+14155238886)");
            }
        }
        if ("make-call-tts".equals(op.name)) {
            String from = first(bodySrc, cfg, "From");
            if (from != null && !from.isBlank() && !TWILIO_E164.matcher(from.strip()).matches()) {
                throw invalidInput("From must be E.164 (+...) for operation make-call-tts");
            }
        }
        if ("send-mms".equals(op.name) || "send-whatsapp".equals(op.name)) {
            String media = first(bodySrc, cfg, "MediaUrl");
            if (media != null && !media.isBlank()
                    && !TWILIO_HTTP_URL.matcher(media.strip()).matches()) {
                throw invalidInput("MediaUrl must be an http(s) URL for operation " + op.name);
            }
        }
    }

    private static boolean requiresTwilioTo(String opName) {
        return "send-sms".equals(opName) || "send-mms".equals(opName)
                || "send-whatsapp".equals(opName) || "make-call-tts".equals(opName);
    }

    private static boolean twilioToAccepted(String to, String opName) {
        if ("send-whatsapp".equals(opName)) {
            return TWILIO_WHATSAPP_ADDR.matcher(to).matches() || TWILIO_E164.matcher(to).matches();
        }
        if (to.regionMatches(true, 0, "whatsapp:", 0, "whatsapp:".length())) {
            return TWILIO_WHATSAPP_ADDR.matcher(to).matches();
        }
        return TWILIO_E164.matcher(to).matches();
    }

    private static final java.util.Set<String> WEBFLOW_ORDER_STATUSES = java.util.Set.of(
            "pending", "refunded", "dispute-lost", "fulfilled", "disputed", "unfulfilled");

    void validateWebflowInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                               Map<String, Object> cfg) {
        if (spec == null || !"webflow".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        List<String> queryFrom = op.queryFrom == null ? List.of() : op.queryFrom;
        if (queryFrom.contains("limit")) {
            Object raw = webflowParamRaw(bodySrc, cfg, "limit");
            if (raw != null) {
                Long n = webflowIntegralParam(raw);
                if (n == null) {
                    throw invalidInput(
                            "limit must be an integer from 1 to 100 for operation " + op.name);
                }
                if (n < 1 || n > 100) {
                    throw invalidInput("limit must be from 1 to 100 for operation " + op.name);
                }
            }
        }
        if (queryFrom.contains("offset")) {
            Object raw = webflowParamRaw(bodySrc, cfg, "offset");
            if (raw != null) {
                Long n = webflowIntegralParam(raw);
                if (n == null) {
                    throw invalidInput(
                            "offset must be a non-negative integer for operation " + op.name);
                }
                if (n < 0) {
                    throw invalidInput("offset must be >= 0 for operation " + op.name);
                }
            }
        }
        if (queryFrom.contains("status") && isWebflowOrderList(op.name)) {
            String status = first(bodySrc, cfg, "status");
            if (status != null && !status.isBlank()) {
                if (!WEBFLOW_ORDER_STATUSES.contains(status.strip().toLowerCase(java.util.Locale.ROOT))) {
                    throw invalidInput(
                            "status must be one of pending, refunded, dispute-lost, fulfilled, disputed, "
                                    + "unfulfilled for operation " + op.name);
                }
            }
        }
    }

    private static boolean isWebflowOrderList(String opName) {
        return "order.list".equals(opName) || "order_list".equals(opName);
    }

    private static Object webflowParamRaw(Map<String, Object> bodySrc, Map<String, Object> cfg, String key) {
        if (bodySrc != null && bodySrc.containsKey(key)) {
            return bodySrc.get(key);
        }
        return cfg == null ? null : cfg.get(key);
    }

    private static Long webflowIntegralParam(Object raw) {
        if (raw instanceof Number num) {
            long v = num.longValue();
            if (num instanceof Double d && d != Math.rint(d)) {
                return null;
            }
            return v;
        }
        if (raw instanceof String s) {
            String t = s.trim();
            if (t.isEmpty()) {
                return null;
            }
            try {
                return Long.parseLong(t);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static final java.util.regex.Pattern EUROSTAT_TIME_PERIOD =
            java.util.regex.Pattern.compile(
                    "^\\d{4}$"
                            + "|^\\d{4}-S[12]$"
                            + "|^\\d{4}-Q[1-4]$"
                            + "|^\\d{4}-\\d{2}$"
                            + "|^\\d{4}-W\\d{2}$"
                            + "|^\\d{4}-\\d{2}-\\d{2}$",
                    java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern EUROSTAT_YEAR_RANGE =
            java.util.regex.Pattern.compile("^\\d{4}-\\d{4}$");
    private static final java.util.Set<String> EUROSTAT_LANGS =
            java.util.Set.of("en", "fr", "de");
    private static final java.util.Set<String> EUROSTAT_GEO_LEVELS = java.util.Set.of(
            "aggregate", "country", "nuts1", "nuts2", "nuts3", "city");

    void validateEurostatInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                Map<String, Object> cfg) {
        if (spec == null || !"eurostat".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String lang = first(bodySrc, cfg, "lang");
        if (lang != null && !lang.isBlank()) {
            if (!EUROSTAT_LANGS.contains(lang.strip().toLowerCase(java.util.Locale.ROOT))) {
                throw invalidInput(
                        "lang must be EN, FR, or DE (case-insensitive) for operation " + op.name);
            }
        }
        if (!"query".equals(op.name)) {
            return;
        }
        String geoLevel = first(bodySrc, cfg, "geoLevel");
        if (geoLevel != null && !geoLevel.isBlank()) {
            if (!EUROSTAT_GEO_LEVELS.contains(geoLevel.strip().toLowerCase(java.util.Locale.ROOT))) {
                throw invalidInput(
                        "geoLevel must be one of aggregate, country, nuts1, nuts2, nuts3, city for operation query");
            }
        }
        boolean hasTime = !blank(bodySrc, "time") || !blank(cfg, "time");
        boolean hasSince = !blank(bodySrc, "sinceTimePeriod") || !blank(cfg, "sinceTimePeriod");
        boolean hasUntil = !blank(bodySrc, "untilTimePeriod") || !blank(cfg, "untilTimePeriod");
        boolean hasLast = !blank(bodySrc, "lastTimePeriod") || !blank(cfg, "lastTimePeriod");
        if (hasTime && (hasSince || hasUntil || hasLast)) {
            throw invalidInput(
                    "use either time (single period) or sinceTimePeriod/untilTimePeriod/lastTimePeriod, "
                            + "not both, for operation query");
        }
        if (hasSince != hasUntil && (hasSince || hasUntil)) {
            throw invalidInput(
                    "sinceTimePeriod and untilTimePeriod must be set together for operation query");
        }
        if (hasLast && (hasSince || hasUntil)) {
            throw invalidInput(
                    "lastTimePeriod cannot be combined with sinceTimePeriod or untilTimePeriod for operation query");
        }
        String time = first(bodySrc, cfg, "time");
        if (time != null && !time.isBlank()) {
            validateEurostatTimePeriod(time.strip(), "time");
        }
        String since = first(bodySrc, cfg, "sinceTimePeriod");
        if (since != null && !since.isBlank()) {
            validateEurostatTimePeriod(since.strip(), "sinceTimePeriod");
        }
        String until = first(bodySrc, cfg, "untilTimePeriod");
        if (until != null && !until.isBlank()) {
            validateEurostatTimePeriod(until.strip(), "untilTimePeriod");
        }
        String last = first(bodySrc, cfg, "lastTimePeriod");
        if (last != null && !last.isBlank()) {
            Long n = webflowIntegralParam(last);
            if (n == null || n < 1) {
                throw invalidInput("lastTimePeriod must be a positive integer for operation query");
            }
        }
    }

    private static void validateEurostatTimePeriod(String value, String field) {
        if (value.contains("--") || EUROSTAT_YEAR_RANGE.matcher(value).matches()) {
            throw invalidInput(field + " must be a single Eurostat TIME_PERIOD (e.g. 2022, 2015-Q4), "
                    + "not a dash range; use sinceTimePeriod and untilTimePeriod on operation query");
        }
        if (!EUROSTAT_TIME_PERIOD.matcher(value).matches()) {
            throw invalidInput(field + " must be a Eurostat TIME_PERIOD code "
                    + "(year 2022, semester 2015-S1, quarter 2015-Q4, month 2015-02, …)");
        }
    }

    private static final java.util.regex.Pattern FRED_API_KEY =
            java.util.regex.Pattern.compile("^[a-z0-9]{32}$");
    private static final java.util.regex.Pattern FRED_OBSERVATION_DATE =
            java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final java.util.Set<String> FRED_UNITS = java.util.Set.of(
            "lin", "chg", "ch1", "pch", "pc1", "pca", "cch", "cca", "log");
    private static final java.util.Set<String> FRED_FREQUENCY = java.util.Set.of(
            "d", "w", "bw", "m", "q", "sa", "a", "wef", "weth", "wew", "wetu", "wem", "wesu", "wesa", "bwew", "bwem");

    void validateFredApiKey(Map<String, Object> cfg) {
        if (spec == null || !"fred".equalsIgnoreCase(spec.key)) {
            return;
        }
        String key = cfg == null ? null : DataSupport.cfgStr(cfg, "api_key", null);
        if (key == null || key.isBlank()) {
            throw invalidInput(
                    "api_key is required in configurationParameters (free FRED key: "
                            + "https://fred.stlouisfed.org/docs/api/api_key.html)");
        }
        if (!FRED_API_KEY.matcher(key.strip()).matches()) {
            throw invalidInput(
                    "api_key must be a 32-character lowercase alphanumeric string (set api_key in "
                            + "configurationParameters — https://fred.stlouisfed.org/docs/api/api_key.html)");
        }
    }

    void validateFredInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                            Map<String, Object> cfg) {
        if (spec == null || !"fred".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        if ("series_search".equals(op.name)) {
            validateFredSearchLimitOffset(bodySrc, cfg);
        }
        if ("series_observations".equals(op.name)) {
            validateFredObservationDates(bodySrc, cfg);
            String units = first(bodySrc, cfg, "units");
            if (units != null && !units.isBlank()
                    && !FRED_UNITS.contains(units.strip().toLowerCase(java.util.Locale.ROOT))) {
                throw invalidInput(
                        "units must be one of lin, chg, ch1, pch, pc1, pca, cch, cca, log for operation "
                                + "series_observations");
            }
            String frequency = first(bodySrc, cfg, "frequency");
            if (frequency != null && !frequency.isBlank()
                    && !FRED_FREQUENCY.contains(frequency.strip().toLowerCase(java.util.Locale.ROOT))) {
                throw invalidInput(
                        "frequency must be a FRED aggregation code (e.g. m, q, a) for operation series_observations");
            }
        }
    }

    private void validateFredSearchLimitOffset(Map<String, Object> bodySrc, Map<String, Object> cfg) {
        Object rawLimit = webflowParamRaw(bodySrc, cfg, "limit");
        if (rawLimit != null) {
            Long n = webflowIntegralParam(rawLimit);
            if (n == null || n < 1 || n > 1000) {
                throw invalidInput("limit must be an integer from 1 to 1000 for operation series_search");
            }
        }
        Object rawOffset = webflowParamRaw(bodySrc, cfg, "offset");
        if (rawOffset != null) {
            Long n = webflowIntegralParam(rawOffset);
            if (n == null || n < 0) {
                throw invalidInput("offset must be a non-negative integer for operation series_search");
            }
        }
    }

    private void validateFredObservationDates(Map<String, Object> bodySrc, Map<String, Object> cfg) {
        for (String field : List.of("observation_start", "observation_end")) {
            String v = first(bodySrc, cfg, field);
            if (v == null || v.isBlank()) {
                continue;
            }
            if (!FRED_OBSERVATION_DATE.matcher(v.strip()).matches()) {
                throw invalidInput(field + " must be YYYY-MM-DD for operation series_observations");
            }
        }
        String start = first(bodySrc, cfg, "observation_start");
        String end = first(bodySrc, cfg, "observation_end");
        if (start != null && !start.isBlank() && end != null && !end.isBlank()
                && start.compareTo(end) > 0) {
            throw invalidInput(
                    "observation_start must be on or before observation_end for operation series_observations");
        }
    }

    private static final java.util.regex.Pattern GLADIA_JOB_ID =
            java.util.regex.Pattern.compile(
                    "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final java.util.Set<String> GLADIA_MODELS = java.util.Set.of("solaria-1", "solaria-3");

    void validateGladiaInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                              Map<String, Object> cfg) {
        if (spec == null || !"gladia".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String apiKey = cfg == null ? null : DataSupport.cfgStr(cfg, "api_key", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw invalidInput(
                    "api_key is required in configurationParameters (Gladia x-gladia-key — "
                            + "https://docs.gladia.io/api-reference/authentication)");
        }
        if ("get_transcript".equals(op.name)) {
            String id = first(bodySrc, cfg, "id");
            if (id == null || id.isBlank()) {
                return;
            }
            if (!GLADIA_JOB_ID.matcher(id.strip()).matches()) {
                throw invalidInput("id must be a UUID for operation get_transcript");
            }
            return;
        }
        if (!"pre_recorded".equals(op.name)) {
            return;
        }
        String audioUrl = first(bodySrc, cfg, "audio_url");
        if (audioUrl != null && !audioUrl.isBlank()
                && !TWILIO_HTTP_URL.matcher(audioUrl.strip()).matches()) {
            throw invalidInput("audio_url must be an http(s) URL for operation pre_recorded");
        }
        String model = first(bodySrc, cfg, "model");
        if (model != null && !model.isBlank()
                && !GLADIA_MODELS.contains(model.strip().toLowerCase(java.util.Locale.ROOT))) {
            throw invalidInput(
                    "model must be solaria-1 or solaria-3 for operation pre_recorded");
        }
        validateGladiaLanguageConfig(bodySrc, cfg);
    }

    private static final java.util.regex.Pattern GUMROAD_DATE =
            java.util.regex.Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    void validateGumroadInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                               Map<String, Object> cfg) {
        if (spec == null || !"gumroad".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        if ("sale_list".equals(op.name)) {
            for (String field : List.of("after", "before")) {
                String v = first(bodySrc, cfg, field);
                if (v != null && !v.isBlank() && !GUMROAD_DATE.matcher(v.strip()).matches()) {
                    throw invalidInput(field + " must be YYYY-MM-DD for operation sale_list");
                }
            }
        }
    }

    private static final java.util.Set<String> HUNTER_DEPARTMENTS = java.util.Set.of(
            "executive", "it", "finance", "management", "sales", "legal", "support", "hr", "marketing",
            "communication", "education", "design", "health", "operations", "product", "research",
            "consulting", "administrative", "procurement");
    private static final java.util.Set<String> HUNTER_REQUIRED_FIELDS =
            java.util.Set.of("full_name", "position", "phone_number");
    private static final java.util.Set<String> HUNTER_SENIORITY =
            java.util.Set.of("junior", "senior", "executive");
    private static final java.util.Set<String> HUNTER_EMAIL_TYPE = java.util.Set.of("personal", "generic");

    void validateHunterInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                              Map<String, Object> cfg) {
        if (spec == null || !"hunter".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String apiKey = cfg == null ? null : DataSupport.cfgStr(cfg, "api_key", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw invalidInput(
                    "api_key is required in configurationParameters (Hunter X-API-KEY — "
                            + "https://hunter.io/api-documentation/v2)");
        }
        if (!"domain.search".equals(op.name)) {
            if ("email.verifier".equals(op.name)) {
                String email = first(bodySrc, cfg, "email");
                if (email != null && !email.isBlank() && !email.contains("@")) {
                    throw invalidInput("email must contain @ for operation email.verifier");
                }
            }
            return;
        }
        Object rawLimit = webflowParamRaw(bodySrc, cfg, "limit");
        if (rawLimit != null) {
            Long n = webflowIntegralParam(rawLimit);
            if (n == null || n < 1 || n > 100) {
                throw invalidInput("limit must be an integer from 1 to 100 for operation domain.search");
            }
        }
        Object rawOffset = webflowParamRaw(bodySrc, cfg, "offset");
        if (rawOffset != null) {
            Long n = webflowIntegralParam(rawOffset);
            if (n == null || n < 0 || n > 10_000) {
                throw invalidInput("offset must be an integer from 0 to 10000 for operation domain.search");
            }
        }
        String type = first(bodySrc, cfg, "type");
        if (type != null && !type.isBlank()
                && !HUNTER_EMAIL_TYPE.contains(type.strip().toLowerCase(java.util.Locale.ROOT))) {
            throw invalidInput("type must be personal or generic for operation domain.search");
        }
        validateHunterCsvSlugs("seniority", first(bodySrc, cfg, "seniority"), HUNTER_SENIORITY, op.name);
        validateHunterCsvSlugs("department", first(bodySrc, cfg, "department"), HUNTER_DEPARTMENTS, op.name);
        validateHunterCsvSlugs(
                "required_field", first(bodySrc, cfg, "required_field"), HUNTER_REQUIRED_FIELDS, op.name);
    }

    void validateIdeogramInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                Map<String, Object> cfg) {
        if (spec == null || !"ideogram".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String apiKey = cfg == null ? null : DataSupport.cfgStr(cfg, "api_key", null);
        if (apiKey == null || apiKey.isBlank()) {
            throw invalidInput(
                    "api_key is required in configurationParameters (Ideogram Api-Key header — "
                            + "https://developer.ideogram.ai/ideogram-api/api-setup)");
        }
        switch (op.name) {
            case "get_task" -> {
                if (blank(bodySrc, "generation_id") && blank(cfg, "generation_id")) {
                    throw invalidInput("generation_id is required for operation get_task");
                }
            }
            case "generate", "generate_async" -> {
                if (blank(bodySrc, "text_prompt") && blank(cfg, "text_prompt")
                        && blank(bodySrc, "json_prompt") && blank(cfg, "json_prompt")) {
                    throw invalidInput(
                            "Supply one of text_prompt or json_prompt for operation " + op.name);
                }
                if (!blank(bodySrc, "text_prompt") && !blank(bodySrc, "json_prompt")) {
                    throw invalidInput("text_prompt and json_prompt are mutually exclusive for operation "
                            + op.name);
                }
                if (!blank(cfg, "text_prompt") && !blank(cfg, "json_prompt")) {
                    throw invalidInput("text_prompt and json_prompt are mutually exclusive for operation "
                            + op.name);
                }
            }
            case "remix" -> {
                if (blank(bodySrc, "image") && blank(cfg, "image")) {
                    throw invalidInput("image (file ref-id) is required for operation remix");
                }
                if (blank(bodySrc, "text_prompt") && blank(cfg, "text_prompt")) {
                    throw invalidInput("text_prompt is required for operation remix");
                }
            }
            case "upscale" -> {
                if (blank(bodySrc, "image_file") && blank(cfg, "image_file")) {
                    throw invalidInput("image_file (file ref-id) is required for operation upscale");
                }
                if (blank(bodySrc, "image_request") && blank(cfg, "image_request")) {
                    throw invalidInput(
                            "image_request JSON string is required for operation upscale (multipart field)");
                }
            }
            default -> { }
        }
    }

    private static void validateHunterCsvSlugs(String field, String raw, java.util.Set<String> allowed, String opName) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String part : raw.split(",")) {
            String t = part.strip().toLowerCase(java.util.Locale.ROOT);
            if (t.isEmpty()) {
                continue;
            }
            if (!allowed.contains(t)) {
                throw invalidInput(field + " value '" + part.strip() + "' is not allowed for operation " + opName
                        + "; allowed: " + String.join(", ", allowed));
            }
        }
    }

    private static final java.util.regex.Pattern HASHNODE_GQL_DOC =
            java.util.regex.Pattern.compile("(?is)(\\b(query|mutation|subscription|fragment)\\b|\\{\\s*__typename)");

    void validateHashnodeInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                Map<String, Object> cfg) {
        if (spec == null || !"hashnode".equalsIgnoreCase(spec.key) || op == null || isIngress(op)) {
            return;
        }
        String token = cfg == null ? null : DataSupport.cfgStr(cfg, "personal_access_token", null);
        if (token == null || token.isBlank()) {
            throw invalidInput(
                    "personal_access_token is required in configurationParameters (Hashnode PAT — "
                            + "https://hashnode.com/settings/developer)");
        }
        String gql = first(bodySrc, cfg, "query");
        if (gql == null || gql.isBlank()) {
            throw invalidInput("query is required for operation " + op.name
                    + " (non-empty GraphQL document on INPUT or in configurationParameters)");
        }
        if (!HASHNODE_GQL_DOC.matcher(gql.strip()).find()) {
            throw invalidInput(
                    "query must be a GraphQL document (query / mutation / subscription / fragment) "
                            + "for operation " + op.name);
        }
    }

    void rejectHashnodeNonGraphqlResponse(DataSupport.HttpResp resp) {
        if (resp == null) {
            return;
        }
        String body = resp.body() == null ? "" : resp.body().stripLeading();
        int status = resp.status();
        if (body.regionMatches(true, 0, "<!DOCTYPE", 0, 9)
                || body.regionMatches(true, 0, "<html", 0, 5)) {
            throw new StudioQuackError(
                    DataSupport.httpErrorCode(status),
                    "Hashnode returned HTML instead of GraphQL JSON — use https://gql.hashnode.com, "
                            + "a valid personal_access_token, and Hashnode Pro if required "
                            + "(https://hashnode.com/changelog/2026-05-13-graphql-api-paid-access)",
                    Map.of("http_status", status));
        }
        Object parsed = DataSupport.parseJson(body);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new StudioQuackError(
                    "REQUEST_FAILED",
                    "Hashnode response is not JSON — expected a GraphQL envelope with data and/or errors",
                    Map.of("http_status", status));
        }
        if (!map.containsKey("data") && !map.containsKey("errors")) {
            throw new StudioQuackError(
                    "REQUEST_FAILED",
                    "Hashnode JSON is not a GraphQL response (missing top-level data or errors)",
                    Map.of("http_status", status));
        }
    }

    private static void validateGladiaLanguageConfig(Map<String, Object> bodySrc, Map<String, Object> cfg) {
        Object raw = bodySrc == null ? null : bodySrc.get("language_config");
        if (raw == null && cfg != null) {
            raw = cfg.get("language_config");
        }
        if (!(raw instanceof Map<?, ?> map)) {
            return;
        }
        if (!map.containsKey("languages")) {
            return;
        }
        Object langs = map.get("languages");
        if (langs instanceof java.util.Collection<?> c && c.isEmpty()) {
            throw invalidInput(
                    "language_config.languages must not be empty when provided for operation pre_recorded");
        }
    }

    static Map<String, Object> mergeSupabaseInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                   Map<String, Object> bodySrc) {
        if (nodeKey == null || !"supabase".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = bodySrc == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bodySrc);
        if (blank(out, "record")) {
            for (String alias : List.of("body", "data", "row", "rows", "payload", "args")) {
                Object v = out.get(alias);
                if (v != null && !(v instanceof String s && s.isBlank())) {
                    out.put("record", v);
                    break;
                }
            }
        }
        out = flattenNestedToolInput(out);
        coerceJsonTextFields(out, "record");
        unwrapValueWrapperFields(out, "record");
        return out;
    }

    void validateSupabaseInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                Map<String, Object> cfg) {
        if (spec == null || !"supabase".equalsIgnoreCase(spec.key)) {
            return;
        }
        if (blank(cfg, "project_url")) {
            throw invalidInput(
                    "project_url must be set in configurationParameters (e.g. https://<project-ref>.supabase.co)");
        }
        if (op != null && "table.delete".equals(op.name) && blank(bodySrc, "id") && blank(cfg, "id")) {
            throw invalidInput(
                    "id is required for operation table.delete (PostgREST filter, e.g. eq.<uuid>)");
        }
    }

    void validateTursoBaseUrl(Map<String, Object> cfg) {
        if (spec == null || !"turso".equalsIgnoreCase(spec.key)) {
            return;
        }
        String base = resolveTursoBaseUrl(spec.key, spec.baseUrl, cfg);
        if (base == null || base.isBlank() || isTursoPlaceholderHost(base)) {
            throw invalidInput(
                    "database_url is required in configuration (HTTPS database origin, e.g. "
                            + "https://mydb-myorg.turso.io)");
        }
    }

    static String resolveTursoBaseUrl(String nodeKey, String baseUrl, Map<String, Object> cfg) {
        if (nodeKey == null || !"turso".equalsIgnoreCase(nodeKey)) {
            return baseUrl;
        }
        String explicit = cfg == null ? null : DataSupport.cfgStr(cfg, "database_url", null);
        if (explicit == null && cfg != null) {
            explicit = DataSupport.cfgStr(cfg, "url", null);
        }
        if (explicit != null && !explicit.isBlank()) {
            return tursoHttpsOrigin(explicit);
        }
        if (cfg != null) {
            String db = DataSupport.cfgStr(cfg, "database_name", null);
            String org = DataSupport.cfgStr(cfg, "organization_slug", null);
            if (db != null && org != null) {
                return "https://" + db.strip() + "-" + org.strip() + ".turso.io";
            }
        }
        if (baseUrl != null && baseUrl.contains("{")) {
            String db = cfg == null ? null : DataSupport.cfgStr(cfg, "database_name", null);
            String org = cfg == null ? null : DataSupport.cfgStr(cfg, "organization_slug", null);
            if (db != null && org != null) {
                return "https://" + db.strip() + "-" + org.strip() + ".turso.io";
            }
        }
        return baseUrl;
    }

    private static boolean isTursoPlaceholderHost(String base) {
        return base.contains("databaseName-organizationSlug")
                || base.contains("{database_name}");
    }

    static String tursoHttpsOrigin(String raw) {
        if (raw == null || raw.isBlank()) {
            return raw;
        }
        String s = raw.strip();
        if (s.regionMatches(true, 0, "libsql://", 0, 9)) {
            s = "https://" + s.substring(9);
        } else if (s.regionMatches(true, 0, "turso://", 0, 8)) {
            s = "https://" + s.substring(8);
        } else if (!s.regionMatches(true, 0, "http://", 0, 7)
                && !s.regionMatches(true, 0, "https://", 0, 8)) {
            s = "https://" + s;
        }
        try {
            java.net.URI u = java.net.URI.create(s);
            if (u.getHost() != null && !u.getHost().isBlank()) {
                int port = u.getPort();
                String portPart = port > 0 ? ":" + port : "";
                return u.getScheme().toLowerCase(java.util.Locale.ROOT) + "://" + u.getHost() + portPart;
            }
        } catch (IllegalArgumentException ignored) {
            // keep normalized string
        }
        while (s.endsWith("/")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    private static String tursoSqlText(Map<String, Object> m, Map<String, Object> cfg) {
        if (m == null) {
            m = Map.of();
        }
        for (String k : List.of("sql", "query", "statement", "command", "script", "code", "stmt")) {
            String s = DataSupport.inStr(m, k, null);
            if (s != null && !s.isBlank()) {
                return s;
            }
        }
        if (cfg != null) {
            String fromCfg = DataSupport.cfgStr(cfg, "sql", null);
            if (fromCfg != null && !fromCfg.isBlank()) {
                return fromCfg;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> tursoNormalizePipeline(Object requests, String topSql,
                                                       Map<String, Object> cfg, Map<String, Object> src) {
        String reqType = DataSupport.inStrAny(src, null, "type");
        if (reqType == null || reqType.isBlank()) {
            reqType = cfg == null ? "execute" : DataSupport.cfgStr(cfg, "type", "execute");
        }
        if (requests instanceof Map<?, ?> map) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : map.entrySet()) {
                m.put(String.valueOf(e.getKey()), e.getValue());
            }
            Object nested = m.get("requests");
            if (nested != null) {
                return tursoNormalizePipeline(nested, topSql, cfg, src);
            }
            if (m.containsKey("stmts") || m.containsKey("queries")) {
                Object list = m.containsKey("stmts") ? m.get("stmts") : m.get("queries");
                return tursoStatementsToPipeline(list, topSql);
            }
            if (tursoLooksLikePipelineStep(m)) {
                return new ArrayList<>(List.of(m));
            }
            String innerSql = tursoSqlText(m, cfg);
            if (innerSql == null) {
                innerSql = topSql;
            }
            if (innerSql != null) {
                return new ArrayList<>(List.of(tursoExecuteStep(innerSql, m, reqType)));
            }
        }
        if (requests instanceof List<?> list && !list.isEmpty()) {
            List<Object> out = new ArrayList<>();
            for (Object item : list) {
                if (item instanceof Map<?, ?> step) {
                    out.add(new LinkedHashMap<>((Map<String, Object>) step));
                } else if (item instanceof String stmt) {
                    out.add(tursoExecuteStep(stmt, Map.of(), reqType));
                }
            }
            return out;
        }
        if (topSql != null && !topSql.isBlank()) {
            Map<String, Object> extras = src == null ? Map.of() : src;
            return new ArrayList<>(List.of(tursoExecuteStep(topSql, extras, reqType)));
        }
        if (requests instanceof Map<?, ?> numeric) {
            List<Object> ordered = tursoNumericKeyedList(numeric);
            if (!ordered.isEmpty()) {
                return tursoNormalizePipeline(ordered, topSql, cfg, src);
            }
        }
        return null;
    }

    private static List<Object> tursoStatementsToPipeline(Object list, String fallbackSql) {
        if (!(list instanceof List<?> stmts)) {
            if (list instanceof String one) {
                return new ArrayList<>(List.of(tursoExecuteStep(one, Map.of(), "execute")));
            }
            if (fallbackSql != null) {
                return new ArrayList<>(List.of(tursoExecuteStep(fallbackSql, Map.of(), "execute")));
            }
            return List.of();
        }
        List<Object> out = new ArrayList<>();
        for (Object o : stmts) {
            if (o instanceof String s) {
                out.add(tursoExecuteStep(s, Map.of(), "execute"));
            } else if (o instanceof Map<?, ?> m) {
                String sql = tursoSqlFromStep(m);
                if (sql != null) {
                    out.add(tursoExecuteStep(sql, (Map<String, Object>) m, "execute"));
                }
            }
        }
        return out;
    }

    private static List<Object> tursoNumericKeyedList(Map<?, ?> map) {
        List<Object> out = new ArrayList<>();
        for (int i = 0; i <= 32; i++) {
            String k = String.valueOf(i);
            if (!map.containsKey(k)) {
                break;
            }
            out.add(map.get(k));
        }
        return out;
    }

    private static boolean tursoLooksLikePipelineStep(Map<String, Object> m) {
        return m.containsKey("type") || m.containsKey("stmt") || m.containsKey("sql");
    }

    private static Map<String, Object> tursoExecuteStep(String sql, Map<String, Object> extras, String type) {
        Map<String, Object> step = new LinkedHashMap<>();
        step.put("type", type == null || type.isBlank() ? "execute" : type);
        Map<String, Object> stmt = new LinkedHashMap<>();
        stmt.put("sql", sql);
        if (extras != null) {
            if (extras.get("args") != null) {
                stmt.put("args", extras.get("args"));
            }
            if (extras.get("named_args") != null) {
                stmt.put("named_args", extras.get("named_args"));
            }
        }
        step.put("stmt", stmt);
        return step;
    }

    private static String tursoSqlFromStep(Map<?, ?> step) {
        if (step == null) {
            return null;
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : step.entrySet()) {
            m.put(String.valueOf(e.getKey()), e.getValue());
        }
        return tursoSqlText(m, null);
    }

    private static boolean tursoPipelineHasSql(List<?> pipeline) {
        if (pipeline == null) {
            return false;
        }
        for (Object item : pipeline) {
            if (item instanceof Map<?, ?> step) {
                String sql = tursoSqlFromStep(step);
                if (sql != null && !sql.isBlank()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void tursoEnsureCloseRequest(List<Object> pipeline) {
        if (pipeline == null || pipeline.isEmpty()) {
            return;
        }
        Object last = pipeline.get(pipeline.size() - 1);
        if (last instanceof Map<?, ?> m && "close".equalsIgnoreCase(String.valueOf(m.get("type")))) {
            return;
        }
        pipeline.add(Map.of("type", "close"));
    }

    void validateTrelloInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                              Map<String, Object> cfg) {
        if (spec == null || !"trello".equalsIgnoreCase(spec.key) || op == null) {
            return;
        }
        if ("board_create".equals(op.name)) {
            requireTrelloField(op, bodySrc, cfg, "name");
        } else if ("label_add".equals(op.name)) {
            requireTrelloField(op, bodySrc, cfg, "idLabel", "value");
        } else if ("comment_create".equals(op.name)) {
            requireTrelloField(op, bodySrc, cfg, "text");
        } else if ("member_add".equals(op.name)) {
            requireTrelloField(op, bodySrc, cfg, "email");
        } else if ("attachment_add".equals(op.name)) {
            if (blank(bodySrc, "url") && blank(cfg, "url") && blank(bodySrc, "file") && blank(cfg, "file")) {
                throw invalidInput("url or file is required for operation attachment_add");
            }
        }
    }

    private void requireTrelloField(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                    Map<String, Object> cfg, String... keys) {
        if (keys == null) {
            return;
        }
        for (String k : keys) {
            if (!blank(bodySrc, k) || !blank(cfg, k)) {
                return;
            }
        }
        throw invalidInput(keys[0] + " is required for operation " + op.name
                + " (set on INPUT or in configurationParameters)");
    }

    static Map<String, Object> mergeTodoistInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                  Map<String, Object> bodySrc) {
        if (nodeKey == null || !"todoist".equalsIgnoreCase(nodeKey) || op == null) {
            return bodySrc;
        }
        Map<String, Object> out = bodySrc == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bodySrc);
        if ("create".equals(op.name) || "update".equals(op.name)) {
            Object pri = out.get("priority");
            if (pri instanceof Number n && n.intValue() == 0) {
                out.remove("priority");
            } else if (pri instanceof String s && (s.isBlank() || "0".equals(s.strip()))) {
                out.remove("priority");
            }
        }
        if ("list".equals(op.name)) {
            if (blank(out, "filter") && !blank(out, "label")) {
                String label = DataSupport.inStr(out, "label", null);
                if (label != null && !label.isBlank()) {
                    out.put("filter", "label:" + label.strip());
                }
            }
        }
        return out;
    }

    private String resolveTodoistListPath(OfficialApiSpec.Operation op, Map<String, Object> in,
                                          Map<String, Object> cfg, String pathTemplate) {
        if (!"todoist".equalsIgnoreCase(spec.key) || op == null || !"list".equals(op.name)) {
            return pathTemplate;
        }
        if (!blank(in, "filter") || !blank(cfg, "filter")) {
            return "/tasks/filter";
        }
        return pathTemplate;
    }

    void validateTodoistInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                               Map<String, Object> cfg) {
        if (spec == null || !"todoist".equalsIgnoreCase(spec.key) || op == null) {
            return;
        }
        if (!"create".equals(op.name) && !"update".equals(op.name)) {
            return;
        }
        Object p = bodySrc == null ? null : bodySrc.get("priority");
        if (p == null && cfg != null) {
            p = cfg.get("priority");
        }
        if (p == null) {
            return;
        }
        String raw = String.valueOf(p).strip();
        if (raw.isEmpty() || "0".equals(raw)) {
            return;
        }
        int v;
        try {
            v = p instanceof Number n ? n.intValue() : Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw invalidInput("priority must be an integer 1–4 (4 is urgent)");
        }
        if (v < 1 || v > 4) {
            throw invalidInput("priority must be 1–4 (4 is urgent); got " + v);
        }
    }

    static Map<String, Object> mergePostmarkInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                   Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"postmark".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        copyAliasIntoCanonical(out, "count", "Count");
        copyAliasIntoCanonical(out, "offset", "Offset");
        copyAliasIntoCanonical(out, "Messages", "messages");
        copyAliasIntoCanonical(out, "TemplateId", "templateId");
        copyAliasIntoCanonical(out, "TemplateAlias", "templateAlias");
        flattenRequestEnvelope(out);
        if ("list-bounces".equals(op.name)) {
            if (blank(out, "count")) {
                out.put("count", 50);
            }
            if (blank(out, "offset")) {
                out.put("offset", 0);
            }
        }
        if ("send-batch".equals(op.name)) {
            Object msgs = out.get("Messages");
            if (msgs instanceof String s) {
                Object parsed = unwrapToolArgsPayload(s);
                if (parsed != null) {
                    msgs = parsed;
                }
            }
            if (msgs instanceof Map<?, ?> one) {
                msgs = List.of(one);
            }
            if (msgs != null) {
                out.put("Messages", msgs);
            }
        }
        if ("send-template".equals(op.name)) {
            unwrapValueWrapperFields(out, "TemplateId", "TemplateAlias");
        }
        return out;
    }

    /** Shodan search: agents often send {@code q} or nest args (DNS arrays use {@link #queryScalar}). */
    static Map<String, Object> mergeShodanInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                 Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"shodan".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        if ("search".equals(op.name)) {
            copyAliasIntoCanonical(out, "query", "q", "Q", "search_query", "searchQuery");
        }
        return out;
    }

    /** Tink: flatten nested tool envelopes (OAuth aliases are {@link #mergeCommonOAuthFormFieldAliases}). */
    static Map<String, Object> mergeTinkInputs(String nodeKey, OfficialApiSpec.Operation op,
                                               Map<String, Object> bodySrc) {
        if (nodeKey == null || !"tink".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        return flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
    }

    /**
     * Tally: path ops use {@code form_id}; webhook body uses official {@code formId}. Agents nest params
     * under {@code config} (handled by {@link #flattenNestedToolInput}) and mix snake/camel case.
     */
    static Map<String, Object> mergeTallyInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                Map<String, Object> bodySrc) {
        if (nodeKey == null || !"tally".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        if (op == null || !"webhook.create".equals(op.name)) {
            copyAliasIntoCanonical(out, "form_id", "formId", "formID");
        }
        if (op == null || "webhook.create".equals(op.name)) {
            copyAliasIntoCanonical(out, "formId", "form_id", "formID");
        }
        copyAliasIntoCanonical(out, "submission_id", "submissionId", "submissionID");
        return out;
    }

    /**
     * Slite writes accept {@code markdown}/{@code html}/{@code sliteml}; agents often send {@code content}.
     * {@code channel_list} has no official {@code /channels} route — treated like {@code note_list}.
     */
    static Map<String, Object> mergeSliteInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                Map<String, Object> bodySrc) {
        if (nodeKey == null || !"slite".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        copyAliasIntoCanonical(out, "noteId", "id", "note_id", "docId", "doc_id");
        if (op != null && ("doc_create".equals(op.name) || "doc_update".equals(op.name))) {
            if (blank(out, "markdown") && !blank(out, "content")) {
                copyAliasIntoCanonical(out, "markdown", "content", "body", "text");
            }
        }
        if (op != null && "doc_get".equals(op.name) && blank(out, "format")) {
            Object fmt = out.get("contentFormat");
            if (fmt != null && !String.valueOf(fmt).isBlank()) {
                out.put("format", fmt);
            }
        }
        return out;
    }

    static Map<String, Object> mergeSonarQubeInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                    Map<String, Object> bodySrc) {
        if (nodeKey == null || op == null || !"sonarqube".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc == null ? Map.of() : bodySrc);
        copyAliasIntoCanonical(out, "organization", "org", "organisation");
        if ("project_create".equals(op.name)) {
            copyAliasIntoCanonical(out, "project", "projectKey", "key");
        }
        if ("project_get".equals(op.name) || "measures_get".equals(op.name)) {
            copyAliasIntoCanonical(out, "component", "projectKey", "key");
        }
        return out;
    }

    static Map<String, Object> mergeShippoInputs(String nodeKey, OfficialApiSpec.Operation op,
                                                 Map<String, Object> bodySrc) {
        if (nodeKey == null || bodySrc == null || !"shippo".equalsIgnoreCase(nodeKey)) {
            return bodySrc;
        }
        Map<String, Object> out = flattenNestedToolInput(bodySrc);
        for (String key : List.of("address_from", "address_to", "parcels", "rate", "shipment")) {
            Object v = out.get(key);
            if (v instanceof String s) {
                Object parsed = unwrapToolArgsPayload(s);
                if (parsed != null) {
                    out.put(key, parsed);
                }
            }
        }
        if (op != null && "address_validate".equals(op.name)) {
            copyAliasIntoCanonical(out, "name", "recipient_name", "full_name");
            copyAliasIntoCanonical(out, "street1", "street_1", "address_line1", "line1");
            copyAliasIntoCanonical(out, "zip", "postal_code", "zip_code", "postcode");
        }
        return out;
    }

    private static void flattenRequestEnvelope(Map<String, Object> out) {
        if (out == null) {
            return;
        }
        for (String key : List.of("Request", "request")) {
            Object req = out.remove(key);
            if (!(req instanceof Map<?, ?> rm)) {
                continue;
            }
            for (var e : rm.entrySet()) {
                if (!(e.getKey() instanceof String k) || k.isBlank()) {
                    continue;
                }
                if (!out.containsKey(k)) {
                    out.put(k, e.getValue());
                }
            }
        }
    }

    /**
     * PostHog Cloud uses separate ingest ({@code *.i.posthog.com}) and app ({@code us.posthog.com}) hosts.
     * A single config {@code host} may point at either; pick the origin that matches the operation path.
     */
    static String resolvePostHogBaseUrl(String nodeKey, OfficialApiSpec.Operation op, String baseUrl) {
        if (nodeKey == null || baseUrl == null || op == null || !"posthog".equalsIgnoreCase(nodeKey)) {
            return baseUrl;
        }
        String path = op.path == null ? "" : op.path;
        if (path.startsWith("/api/")) {
            return postHogAppHost(baseUrl.strip());
        }
        if (path.startsWith("/i/") || path.startsWith("/flags")) {
            return postHogIngestHost(baseUrl.strip());
        }
        return baseUrl;
    }

    static String postHogIngestHost(String base) {
        if (base.contains(".i.posthog.com")) {
            return base;
        }
        return base
                .replace("https://us.posthog.com", "https://us.i.posthog.com")
                .replace("http://us.posthog.com", "http://us.i.posthog.com")
                .replace("https://eu.posthog.com", "https://eu.i.posthog.com")
                .replace("http://eu.posthog.com", "http://eu.i.posthog.com");
    }

    static String postHogAppHost(String base) {
        if (base.contains(".posthog.com") && !base.contains(".i.posthog.com")) {
            return base;
        }
        return base
                .replace("https://us.i.posthog.com", "https://us.posthog.com")
                .replace("http://us.i.posthog.com", "http://us.posthog.com")
                .replace("https://eu.i.posthog.com", "https://eu.posthog.com")
                .replace("http://eu.i.posthog.com", "http://eu.posthog.com");
    }

    /** US hosts are packaged; EU accounts need api.eu.newrelic.com and the EU Event API collector. */
    static String applyNewRelicDataRegionHosts(String nodeKey, String baseUrl, Map<String, Object> cfg) {
        if (nodeKey == null || baseUrl == null || !"new-relic".equalsIgnoreCase(nodeKey)) {
            return baseUrl;
        }
        String region = DataSupport.cfgStr(cfg, "data_region", "us");
        if (region == null || !"eu".equalsIgnoreCase(region.strip())) {
            return baseUrl;
        }
        String base = baseUrl;
        base = base.replace("https://api.newrelic.com", "https://api.eu.newrelic.com");
        base = base.replace("https://insights-collector.newrelic.com",
                "https://insights-collector.eu01.nr-data.net");
        return base;
    }

    /**
     * Bitbucket: some callers use Graph-ish names ({@code pull_request_id}) instead of path {@code {id}}.
     */
    static Map<String, Object> mergeBitbucketPathAliases(Map<String, Object> bodySrc) {
        if (bodySrc == null || bodySrc.isEmpty()) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        copyAliasIntoCanonical(out, "id", "pull_request_id", "pr_id", "pullRequestId");
        copyAliasIntoCanonical(out, "pipeline_uuid", "pipelineUuid", "pipelineId", "pipeline_id");
        return out;
    }

    private static void copyAliasIntoCanonical(Map<String, Object> m, String canonical, String... aliases) {
        if (m == null || !blank(m, canonical)) {
            return;
        }
        for (String a : aliases) {
            if (a == null || a.isBlank()) {
                continue;
            }
            if (!blank(m, a)) {
                m.put(canonical, m.get(a));
                return;
            }
        }
    }

    /**
     * {@code host_and_path} is host + path + query. {@link #SEGMENT_PATH_KEYS}
     * are multi-segment paths. Every other placeholder is one segment, so
     * {@code /} becomes {@code %2F}.
     */
    private static String encodePathValue(String key, String val) {
        if ("host_and_path".equals(key)) {
            return DataSupport.urlEncodeHostAndPath(val);
        }
        if (SEGMENT_PATH_KEYS.contains(key)) {
            return DataSupport.urlEncodeSegments(val);
        }
        // {tokenHost} and {store_host} sit in the path template. Encoding ':'
        // turns a port into an illegal host. '@' still encodes (no userinfo).
        if (isHostKey(key) && DataSupport.isHostLiteral(val)) {
            return val;
        }
        return DataSupport.urlEncode(val);
    }

    private static boolean isHostKey(String key) {
        return key.endsWith("Host") || key.endsWith("_host");
    }

    /**
     * JSON stream path: map + force the request flag. Other bodyFormats skip the
     * JSON flag (it is meaningless on form/multipart/raw).
     */
    private static OfficialApiBody.Prepared prepareStreamBody(OfficialApiSpec.Operation op,
                                                              Map<String, Object> in, int timeout) {
        String format = op.bodyFormat == null ? "json" : op.bodyFormat;
        if ("json".equals(format)) {
            Map<String, Object> bodyMap = OfficialApiStream.applyRequestFlag(
                    OfficialApiBody.jsonObject(op, in), op.stream);
            return OfficialApiBody.jsonFromMap(bodyMap, op.contentType);
        }
        return OfficialApiBody.encode(op, in, timeout);
    }

    /** GET/HEAD never send a body. DELETE sends one only when {@code bodyFrom} is non-empty. */
    static boolean shouldSendBody(OfficialApiSpec.Operation op) {
        if (op == null) {
            return false;
        }
        if ("GET".equals(op.method) || "HEAD".equals(op.method)) {
            return false;
        }
        if ("DELETE".equals(op.method)) {
            return op.bodyFrom != null && !op.bodyFrom.isEmpty();
        }
        return true;
    }

    private String applyAuth(Map<String, String> headers, Map<String, Object> cfg) {
        String type = spec.authType == null ? "none" : spec.authType;
        if (type.isBlank() || "none".equals(type)) {
            return null;
        }
        String secret = DataSupport.cfgStr(cfg, spec.authConfigKey, null);
        if (secret == null || secret.isBlank()) {
            return spec.authConfigKey + " is required (StudioQuack secret binding)";
        }
        switch (type) {
            case "bearer" -> headers.put(spec.authHeader, (spec.authPrefix.isBlank() ? "Bearer " : spec.authPrefix) + secret);
            case "header" -> headers.put(spec.authHeader, spec.authPrefix + secret);
            case "basic" -> {
                String user = DataSupport.cfgStr(cfg, spec.authUserKey, "");
                String pair = "username".equals(spec.basicKeyPosition)
                        ? secret + ":"
                        : (user == null ? "" : user) + ":" + secret;
                String token = Base64.getEncoder().encodeToString(pair.getBytes(StandardCharsets.UTF_8));
                headers.put("Authorization", "Basic " + token);
            }
            case "query" -> {
                // query-string auth is applied in buildUrl via config; keep header empty
            }
            case "aws-sigv4" -> {
                String secretKey = DataSupport.cfgStr(cfg, spec.secretKeyConfigKey, null);
                if (secretKey == null || secretKey.isBlank()) {
                    return spec.secretKeyConfigKey + " is required (StudioQuack secret binding)";
                }
                if (spec.sigv4 == null) {
                    return "sigv4 block is required for auth.type aws-sigv4";
                }
            }
            default -> headers.put(spec.authHeader, spec.authPrefix + secret);
        }
        return null;
    }

    /**
     * Stamp SigV4 headers after the body is final. No-op for other auth types.
     * Never logs credentials or the payload.
     */
    private String applySigV4(Map<String, String> headers, String method, String url, byte[] body,
                              Map<String, Object> cfg, Map<String, Object> in, Instant now) {
        if (!"aws-sigv4".equals(spec.authType)) {
            return null;
        }
        OfficialApiSpec.SigV4Block sv = spec.sigv4;
        if (sv == null) {
            return "sigv4 block is required for auth.type aws-sigv4";
        }
        String accessKey = DataSupport.cfgStr(cfg, spec.authConfigKey, null);
        String secretKey = DataSupport.cfgStr(cfg, spec.secretKeyConfigKey, null);
        String region = first(in, cfg, sv.regionConfigKey);
        String service = sv.service != null && !sv.service.isBlank()
                ? sv.service
                : first(in, cfg, sv.serviceConfigKey);
        if (service == null || service.isBlank()) {
            return "sigv4 service is required";
        }
        if (region == null || region.isBlank()) {
            return sv.regionConfigKey + " is required";
        }
        String session = sv.sessionTokenConfigKey.isBlank()
                ? null
                : DataSupport.cfgStr(cfg, sv.sessionTokenConfigKey, null);
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            return "invalid URL";
        }
        return AwsSigV4.sign(headers, method, uri, body, accessKey, secretKey, region, service,
                session, now);
    }

    /**
     * INPUT {@code operation} wins, then config. INPUT {@code action} is an operation
     * alias only when it names a real operation — several APIs (Telegram
     * send-chat-action, Mautic contact.dnc) take {@code action} as a request field.
     */
    private String operationName(Map<String, Object> in, Map<String, Object> cfg) {
        String op = DataSupport.inStr(in, "operation", null);
        if (op != null) {
            return op;
        }
        String configured = DataSupport.cfgStr(cfg, "operation", "");
        String action = DataSupport.inStr(in, "action", null);
        if (action != null) {
            OfficialApiSpec.Operation named = spec.operation(action);
            if (named != null) {
                return action;
            }
            if (!spec.operationStrict && configured.isEmpty()) {
                return action;
            }
        }
        return configured;
    }

    /**
     * {@code llm_input} wins for {@code operation}/{@code action} so FUNCTION-port payloads dispatch
     * correctly; instance config still fills when the port omits the verb.
     */
    private String resolveOperationName(Map<String, Object> llmIn, Map<String, Object> in, Map<String, Object> cfg) {
        if (explicitBlankOperation(llmIn) || explicitBlankOperation(in) || explicitBlankOperation(cfg)) {
            throw invalidInput("operation cannot be blank or whitespace. Accepted: " + spec.operations.keySet());
        }
        String opName = DataSupport.inStrAny(llmIn, null, "operation", "action");
        if (opName == null || opName.isBlank()) {
            opName = operationName(in, cfg);
        }
        if (opName != null && !opName.isBlank() && spec.operation(opName) == null) {
            throw invalidInput("Unknown operation: " + opName + ". Accepted: " + spec.operations.keySet());
        }
        if (opName == null || opName.isBlank()) {
            if (spec.operations.size() == 1) {
                opName = spec.operations.keySet().iterator().next();
            } else {
                throw invalidInput("operation is required. Accepted: " + spec.operations.keySet());
            }
        }
        return opName;
    }

    /**
     * True when {@code operation} is present but null/blank — do not fall back to config default
     * (tool calls often send {@code ""}).
     */
    private static boolean explicitBlankOperation(Map<String, Object> m) {
        if (m == null || !m.containsKey("operation")) {
            return false;
        }
        Object v = m.get("operation");
        if (v == null) {
            return true;
        }
        if (v instanceof String s) {
            return s.isBlank();
        }
        String t = String.valueOf(v).trim();
        return t.isEmpty();
    }

    /**
     * When {@code operation} mismatches the payload (default {@code chat} in config while the port
     * sends embed/rerank fields only on {@code llm_input}), infer the REST verb from Cohere-shaped keys.
     */
    private String inferCohereOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                        boolean explicitOperationOnPort) {
        if (mergedPorts == null) {
            mergedPorts = Map.of();
        }
        OfficialApiSpec.Operation declared = spec.operation(opName);
        if (declared != null && coherePayloadMatchesOperation(declared, mergedPorts, cfg)) {
            return null;
        }
        if (spec.operationStrict && declared == null) {
            return null;
        }
        if (explicitOperationOnPort) {
            return null;
        }
        if (!cohereHasMessages(mergedPorts, cfg)) {
            if (!blank(mergedPorts, "query") || !blank(mergedPorts, "documents")) {
                return "rerank";
            }
        }
        // Cohere retired POST /v1/classify on 2025-09-25. inputs now means embed, not classify.
        boolean embedish = !blank(mergedPorts, "input_type")
                || !blank(mergedPorts, "texts")
                || !blank(mergedPorts, "images")
                || !blank(mergedPorts, "inputs");
        if (embedish && blank(mergedPorts, "messages")) {
            return "embed";
        }
        if (!blank(mergedPorts, "text") && blank(mergedPorts, "messages")
                && blank(mergedPorts, "input_type")
                && blank(mergedPorts, "texts")
                && blank(mergedPorts, "images")
                && blank(mergedPorts, "inputs")) {
            return "tokenize";
        }
        if (cohereHasMessages(mergedPorts, cfg) && !"chat".equals(opName)) {
            return "chat";
        }
        return null;
    }

    /**
     * Cohere exposes only {@code llm_input}; AI tools often still route the DuckTape {@code request}
     * payload to a virtual {@code input} port. Merge both so {@code operation} and embed fields survive.
     */
    static Map<String, Object> mergeMinimaxUnifiedPort(Map<String, Object> inputPort, Map<String, Object> llmInput) {
        Map<String, Object> merged = OfficialApiStream.mergeInput(inputPort, llmInput);
        merged = flattenNestedToolInput(merged);
        coerceCohereJsonListFields(merged, "messages");
        return merged;
    }

    /**
     * When config defaults to {@code chat}, route tts/stt-shaped {@code llm_input} without an explicit
     * {@code operation} override (same pattern as Cohere embed inference).
     */
    private String inferGroqOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                      boolean explicitOperationOnPort) {
        if (explicitOperationOnPort || mergedPorts == null) {
            return null;
        }
        if (!"chat".equals(opName)) {
            return null;
        }
        if (!blank(mergedPorts, "input") && !blank(mergedPorts, "voice")) {
            return "tts";
        }
        if (!blank(mergedPorts, "file") || !blank(mergedPorts, "url")) {
            return "stt";
        }
        return null;
    }

    /**
     * Agent tools omit {@code operation}; config defaults to {@code pre_recorded}. Route {@code id}-only polls
     * to {@code get_transcript}; {@code audio_url} (with or without {@code id}) stays on submit.
     */
    private String inferIdeogramOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                          boolean explicitOperationOnPort) {
        if (mergedPorts == null) {
            mergedPorts = Map.of();
        }
        if (explicitOperationOnPort) {
            return null;
        }
        boolean hasGenId = !blank(mergedPorts, "generation_id");
        boolean hasPrompt = !blank(mergedPorts, "text_prompt") || !blank(mergedPorts, "json_prompt");
        boolean hasRemixImage = !blank(mergedPorts, "image");
        boolean hasUpscaleFile = !blank(mergedPorts, "image_file");
        if (hasGenId && !hasPrompt && !hasRemixImage && !hasUpscaleFile) {
            return "get_task";
        }
        if (hasUpscaleFile) {
            return "upscale";
        }
        if (hasRemixImage && !blank(mergedPorts, "text_prompt")) {
            return "remix";
        }
        if (!blank(mergedPorts, "webhook_url") && hasPrompt) {
            return "generate_async";
        }
        return null;
    }

    private String inferGladiaOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                        boolean explicitOperationOnPort) {
        if (mergedPorts == null) {
            mergedPorts = Map.of();
        }
        if (explicitOperationOnPort) {
            return null;
        }
        if (!blank(mergedPorts, "audio_url")) {
            return "pre_recorded";
        }
        if (!blank(mergedPorts, "id")) {
            return "get_transcript";
        }
        return null;
    }

    private String inferMinimaxOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                         boolean explicitOperationOnPort) {
        if (mergedPorts == null) {
            mergedPorts = Map.of();
        }
        OfficialApiSpec.Operation declared = spec.operation(opName);
        if (declared != null && minimaxPayloadMatchesOperation(declared, mergedPorts, cfg)) {
            return null;
        }
        if (explicitOperationOnPort) {
            return null;
        }
        if (!minimaxHasMessages(mergedPorts, cfg)) {
            if (!blank(mergedPorts, "prompt")) {
                return "generate_image";
            }
            if (!blank(mergedPorts, "text")) {
                return "tts";
            }
            if (!blank(mergedPorts, "content") || !blank(mergedPorts, "resolution")
                    || !blank(mergedPorts, "duration") || !blank(mergedPorts, "ratio")) {
                return "generate_video";
            }
        }
        if (minimaxHasMessages(mergedPorts, cfg) && !"chat".equals(opName)) {
            return "chat";
        }
        return null;
    }

    private static boolean minimaxPayloadMatchesOperation(OfficialApiSpec.Operation op,
                                                            Map<String, Object> mergedPorts,
                                                            Map<String, Object> cfg) {
        if (op == null) {
            return false;
        }
        return switch (op.name) {
            case "chat" -> minimaxHasMessages(mergedPorts, cfg);
            case "generate_image" -> !blank(mergedPorts, "prompt");
            case "tts" -> !blank(mergedPorts, "text");
            case "generate_video" -> !blank(mergedPorts, "content") || !blank(mergedPorts, "prompt")
                    || !blank(mergedPorts, "model") || !blank(cfg, "model");
            default -> true;
        };
    }

    private static boolean minimaxHasMessages(Map<String, Object> mergedPorts, Map<String, Object> cfg) {
        if (!blank(mergedPorts, "messages")) {
            return true;
        }
        return cfg != null && !blank(cfg, "messages");
    }

    /**
     * Agent tools often omit {@code operation}; instance config defaults to {@code generate}. Route
     * id-only polls to {@code get_result} and align other verbs with Stable Image payload shape.
     */
    private String inferStabilityOperation(String opName, Map<String, Object> mergedPorts, Map<String, Object> cfg,
                                           boolean explicitOperationOnPort) {
        if (mergedPorts == null) {
            mergedPorts = Map.of();
        }
        if (explicitOperationOnPort) {
            return null;
        }
        if (!blank(mergedPorts, "id")
                && blank(mergedPorts, "prompt")
                && blank(mergedPorts, "image")) {
            return "get_result";
        }
        OfficialApiSpec.Operation declared = spec.operation(opName);
        if (declared != null && stabilityPayloadMatchesOperation(declared, mergedPorts, cfg)) {
            return null;
        }
        if (!blank(mergedPorts, "mask") && !blank(mergedPorts, "image")) {
            return "edit";
        }
        if (!blank(mergedPorts, "image") && !blank(mergedPorts, "prompt")) {
            String cfgOp = DataSupport.cfgStr(cfg, "operation", "");
            if ("upscale".equals(cfgOp) || "edit".equals(cfgOp) || "control".equals(cfgOp)) {
                return cfgOp;
            }
            return "generate";
        }
        if (!blank(mergedPorts, "prompt")) {
            return "generate";
        }
        return null;
    }

    private static boolean stabilityPayloadMatchesOperation(OfficialApiSpec.Operation op,
                                                            Map<String, Object> mergedPorts,
                                                            Map<String, Object> cfg) {
        if (op == null) {
            return false;
        }
        return switch (op.name) {
            case "get_result" -> !blank(mergedPorts, "id");
            case "generate" -> !blank(mergedPorts, "prompt");
            case "upscale", "control" -> !blank(mergedPorts, "image") && !blank(mergedPorts, "prompt");
            case "edit" -> !blank(mergedPorts, "image");
            default -> true;
        };
    }

    void validateMinimaxInputs(OfficialApiSpec.Operation op, Map<String, Object> bodySrc, Map<String, Object> cfg) {
        if (spec == null || op == null || !"minimax".equalsIgnoreCase(spec.key)) {
            return;
        }
        if (!"chat".equals(op.name)) {
            return;
        }
        Object messages = bodySrc == null ? null : bodySrc.get("messages");
        if (messages == null && cfg != null) {
            messages = cfg.get("messages");
        }
        if (messages instanceof String) {
            throw invalidInput("messages must be an array of {role, content} objects for operation chat");
        }
    }

    static Map<String, Object> mergeCohereUnifiedPort(Map<String, Object> inputPort, Map<String, Object> llmInput) {
        Map<String, Object> merged = OfficialApiStream.mergeInput(inputPort, llmInput);
        merged = flattenNestedToolInput(merged);
        coerceCohereJsonListFields(merged, "messages", "texts", "documents", "inputs", "tools");
        return merged;
    }

    private static boolean cohereExplicitOperation(Map<String, Object> mergedPorts) {
        if (mergedPorts == null || !mergedPorts.containsKey("operation")) {
            return false;
        }
        return !blank(mergedPorts, "operation");
    }

    private static void coerceCohereJsonListFields(Map<String, Object> m, String... keys) {
        if (m == null || keys == null) {
            return;
        }
        for (String key : keys) {
            Object v = m.get(key);
            if (v instanceof String s) {
                Object parsed = unwrapToolArgsPayload(s);
                if (parsed != null) {
                    m.put(key, parsed);
                }
            }
        }
    }

    private static boolean coherePayloadMatchesOperation(OfficialApiSpec.Operation op, Map<String, Object> mergedPorts,
                                                         Map<String, Object> cfg) {
        if (op == null) {
            return false;
        }
        return switch (op.name) {
            case "chat" -> cohereHasMessages(mergedPorts, cfg);
            case "embed" -> !blank(mergedPorts, "input_type")
                    && (!blank(mergedPorts, "texts") || !blank(mergedPorts, "images") || !blank(mergedPorts, "inputs"));
            case "rerank" -> !blank(mergedPorts, "query") && !blank(mergedPorts, "documents");
            case "tokenize" -> !blank(mergedPorts, "text");
            default -> true;
        };
    }

    private static boolean cohereHasMessages(Map<String, Object> mergedPorts, Map<String, Object> cfg) {
        if (!blank(mergedPorts, "messages")) {
            return true;
        }
        return cfg != null && !blank(cfg, "messages");
    }

    /**
     * Embed API expects {@code texts} array; accept a single {@code text} from docs/samples.
     */
    static Map<String, Object> mergeCohereEmbedTextsIfNeeded(OfficialApiSpec.Operation op, Map<String, Object> bodySrc) {
        if (op == null || !"embed".equals(op.name) || bodySrc == null) {
            return bodySrc;
        }
        if (!blank(bodySrc, "texts")) {
            return bodySrc;
        }
        Object t = bodySrc.get("text");
        if (t == null || String.valueOf(t).isBlank()) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("texts", List.of(t));
        return out;
    }

    private static final Map<String, String> COHERE_OPERATION_MODEL_DEFAULT = Map.of(
            "embed", "embed-v4.0",
            "rerank", "rerank-v4.0-pro");

    static Map<String, Object> mergeCohereEmbedDefaults(OfficialApiSpec.Operation op, Map<String, Object> bodySrc) {
        if (op == null || !"embed".equals(op.name) || bodySrc == null) {
            return bodySrc;
        }
        if (!blank(bodySrc, "input_type")) {
            return bodySrc;
        }
        if (blank(bodySrc, "texts") && blank(bodySrc, "images") && blank(bodySrc, "inputs")) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("input_type", "search_document");
        return out;
    }

    static Map<String, Object> mergeCohereOperationModel(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                                           Map<String, Object> cfg) {
        if (op == null || bodySrc == null) {
            return bodySrc;
        }
        String fallback = COHERE_OPERATION_MODEL_DEFAULT.get(op.name);
        if (fallback == null) {
            return bodySrc;
        }
        String model = first(bodySrc, cfg, "model");
        if (model == null || model.isBlank()) {
            Map<String, Object> out = new LinkedHashMap<>(bodySrc);
            out.put("model", fallback);
            return out;
        }
        String m = model.strip();
        boolean compatible = switch (op.name) {
            case "embed" -> m.regionMatches(true, 0, "embed", 0, 5);
            case "rerank" -> m.regionMatches(true, 0, "rerank", 0, 6);
            default -> true;
        };
        if (compatible) {
            return bodySrc;
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("model", fallback);
        return out;
    }

    /**
     * Same precedence as path/query/header fields: a non-blank INPUT value wins,
     * otherwise the instance config value is used. Limited to this operation's
     * {@code bodyFrom} (+ raw {@code bodyParam}) so non-body config (api_key,
     * timeouts) never reaches the wire, including ops whose empty bodyFrom sends
     * the whole input. Config values arrive as text; they are typed by the
     * parameter's declared type so numbers/booleans/objects keep their JSON shape.
     */
    static Map<String, Object> underlayBodyConfig(OfficialApiSpec.Operation op, Map<String, Object> in,
                                                  Map<String, Object> cfg, Map<String, String> cfgTypes) {
        if (op == null || cfg == null || cfg.isEmpty()) {
            return in;
        }
        List<String> keys = new java.util.ArrayList<>(op.bodyFrom);
        if ("raw".equals(op.bodyFormat) && op.bodyParam != null && !op.bodyParam.isBlank()) {
            keys.add(op.bodyParam);
        }
        if (op.mime != null) {
            keys.addAll(OfficialApiSpec.MimeBlock.FIELDS);
        }
        Map<String, Object> out = null;
        for (String k : keys) {
            if (blank(in, k) && !blank(cfg, k)) {
                if (out == null) {
                    out = in == null ? new LinkedHashMap<>() : new LinkedHashMap<>(in);
                }
                out.put(k, typedConfig(cfg.get(k), cfgTypes == null ? null : cfgTypes.get(k)));
            }
        }
        return out == null ? in : out;
    }

    /**
     * Fills {@link OfficialApiSpec.Operation#bodyDefaults} into the wire body when INPUT/config
     * omitted a key or supplied only whitespace (same emptiness rule as {@link #blank}).
     */
    static Map<String, Object> mergeBodyDefaults(OfficialApiSpec.Operation op, Map<String, Object> wireBody) {
        if (op == null || op.bodyDefaults == null || op.bodyDefaults.isEmpty()) {
            return wireBody;
        }
        Map<String, Object> out = wireBody == null ? new LinkedHashMap<>() : new LinkedHashMap<>(wireBody);
        for (Map.Entry<String, Object> e : op.bodyDefaults.entrySet()) {
            String k = e.getKey();
            if (k == null || k.isBlank()) {
                continue;
            }
            if (blank(out, k)) {
                out.put(k, e.getValue());
            }
        }
        return out;
    }

    /**
     * Calendly requires {@code user} or {@code organization} on GET /event_types and GET /scheduled_events.
     * When both are omitted, resolve the caller's user URI once via GET /users/me and set {@code user}.
     */
    Map<String, Object> mergeCalendlyUserFromMeIfNeeded(OfficialApiSpec.Operation op, Map<String, Object> bodySrc,
                                                       Map<String, Object> cfg, Map<String, String> headers,
                                                       int timeoutSeconds) {
        lastResolvedCalendlyUserUri = null;
        if (spec == null || op == null || !"calendly".equalsIgnoreCase(spec.key)) {
            return bodySrc;
        }
        if (!"GET".equals(op.method) || !CALENDLY_ME_PREFILL_OPS.contains(op.name)) {
            return bodySrc;
        }
        if (op.queryFrom == null || !op.queryFrom.contains("user") || !op.queryFrom.contains("organization")) {
            return bodySrc;
        }
        if (first(bodySrc, cfg, "user") != null || first(bodySrc, cfg, "organization") != null) {
            return bodySrc;
        }
        String token = DataSupport.cfgStr(cfg, spec.authConfigKey, null);
        if (token == null || token.isBlank()) {
            return bodySrc;
        }
        String uri = calendlyUserUriByToken.computeIfAbsent(token, t -> fetchCalendlyCurrentUserUri(headers, timeoutSeconds));
        if (uri == null || uri.isBlank()) {
            return bodySrc;
        }
        lastResolvedCalendlyUserUri = uri;
        Map<String, Object> out = bodySrc == null ? new LinkedHashMap<>() : new LinkedHashMap<>(bodySrc);
        out.putIfAbsent("user", uri);
        return out;
    }

    private String fetchCalendlyCurrentUserUri(Map<String, String> headers, int timeoutSeconds) {
        String base = spec.baseUrl == null ? "" : spec.baseUrl.trim();
        if (base.isBlank()) {
            return null;
        }
        String meUrl = base.endsWith("/") ? base + "users/me" : base + "/users/me";
        DataSupport.HttpResp resp;
        try {
            resp = DataSupport.exchangeBytes(null, "GET", meUrl, headers, null, timeoutSeconds);
        } catch (RuntimeException e) {
            return null;
        }
        if (resp.error() != null && !resp.error().isBlank()) {
            return null;
        }
        if (resp.status() < 200 || resp.status() >= 300) {
            return null;
        }
        return calendlyUserUriFromUsersMeJson(resp.body());
    }

    private static String calendlyUserUriFromUsersMeJson(String body) {
        Object parsed = DataSupport.parseJson(body);
        if (!(parsed instanceof Map<?, ?> top)) {
            return null;
        }
        Object resource = top.get("resource");
        if (!(resource instanceof Map<?, ?> rm)) {
            return null;
        }
        Object uri = rm.get("uri");
        if (uri instanceof String s && !s.isBlank()) {
            return s;
        }
        return null;
    }

    private static Object typedConfig(Object value, String type) {
        if (!(value instanceof String s) || type == null) {
            return value;
        }
        String t = s.trim();
        switch (type) {
            case "number" -> {
                try {
                    return t.matches("-?\\d{1,18}") ? (Object) Long.parseLong(t) : (Object) Double.parseDouble(t);
                } catch (NumberFormatException e) {
                    return value;
                }
            }
            case "boolean" -> {
                return "true".equalsIgnoreCase(t) ? Boolean.TRUE : "false".equalsIgnoreCase(t) ? Boolean.FALSE : value;
            }
            case "object", "array", "list" -> {
                if (t.startsWith("{") || t.startsWith("[")) {
                    try {
                        return studioquack.Json.parse(t);
                    } catch (RuntimeException e) {
                        return value;
                    }
                }
                return value;
            }
            default -> {
                return value;
            }
        }
    }

    private static String first(Map<String, Object> in, Map<String, Object> cfg, String key) {
        String a = queryScalar(in, key);
        return a != null ? a : queryScalar(cfg, key);
    }

    /**
     * Query-string scalar. JSON numbers often arrive as {@link Double}, and
     * {@code String.valueOf(1.0d)} is {@code "1.0"}, which integer parameters
     * (MediaWiki {@code srlimit}, {@code sroffset}, …) reject.
     */
    static String queryScalar(Map<String, Object> m, String key) {
        if (m == null || key == null) {
            return null;
        }
        Object v = m.get(key);
        if (v instanceof Number n) {
            String rendered = DataSupport.integralText(n);
            return rendered.isEmpty() ? null : rendered;
        }
        if (v instanceof Collection<?> c) {
            String joined = commaSeparatedQueryValue(c);
            return joined == null || joined.isEmpty() ? null : joined;
        }
        if (v != null && v.getClass().isArray()) {
            int n = java.lang.reflect.Array.getLength(v);
            List<String> parts = new ArrayList<>(n);
            for (int i = 0; i < n; i++) {
                Object item = java.lang.reflect.Array.get(v, i);
                if (item == null) {
                    continue;
                }
                String s = String.valueOf(item).trim();
                if (!s.isEmpty()) {
                    parts.add(s);
                }
            }
            if (parts.isEmpty()) {
                return null;
            }
            return String.join(",", parts);
        }
        return DataSupport.inStr(m, key, null);
    }

    static String commaSeparatedQueryValue(Collection<?> c) {
        if (c == null || c.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (Object item : c) {
            if (item == null) {
                continue;
            }
            String s = String.valueOf(item).trim();
            if (!s.isEmpty()) {
                parts.add(s);
            }
        }
        return parts.isEmpty() ? null : String.join(",", parts);
    }

    /**
     * Empty Collection / array is blank so a required platform file list {@code []}
     * fails fast. Empty Map stays non-blank ({@code inStr} stringifies it to {@code "{}"}).
     */
    /**
     * Identify and Group Identify take {@code application/x-www-form-urlencoded}
     * with {@code identification} set to a JSON array. A single object is wrapped
     * so Amplitude does not answer {@code missing_event}.
     */
    static Map<String, Object> mergeAmplitudeIdentification(OfficialApiSpec.Operation op,
                                                            Map<String, Object> bodySrc) {
        if (op == null || bodySrc == null) {
            return bodySrc;
        }
        if (!"identify".equals(op.name) && !"group.identify".equals(op.name)) {
            return bodySrc;
        }
        Object raw = bodySrc.get("identification");
        if (raw == null) {
            return bodySrc;
        }
        Object parsed = raw;
        if (raw instanceof String s && !s.isBlank()) {
            Object json = DataSupport.parseJson(s.trim());
            if (json != null) {
                parsed = json;
            }
        }
        List<Map<String, Object>> items = new ArrayList<>();
        if (parsed instanceof Map<?, ?> map) {
            items.add(stringKeyMap(map));
        } else if (parsed instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    items.add(stringKeyMap(map));
                } else if (item instanceof String s) {
                    Object json = DataSupport.parseJson(s);
                    if (json instanceof Map<?, ?> map) {
                        items.add(stringKeyMap(map));
                    }
                }
            }
        } else {
            return bodySrc;
        }
        if (items.isEmpty()) {
            throw invalidInput("identification is required for operation " + op.name);
        }
        boolean group = "group.identify".equals(op.name);
        for (Map<String, Object> item : items) {
            if (group) {
                if (blank(item, "group_type") || blank(item, "group_value")) {
                    throw invalidInput("identification needs group_type and group_value");
                }
            } else if (blank(item, "user_id") && blank(item, "device_id")) {
                throw invalidInput("identification needs user_id or device_id");
            }
        }
        Map<String, Object> out = new LinkedHashMap<>(bodySrc);
        out.put("identification", items);
        return out;
    }

    /** Export hours are {@code YYYYMMDDTHH}, end is not before start, span is at most 365 days. */
    static void validateAmplitudeExport(OfficialApiSpec.Operation op, Map<String, Object> in,
                                        Map<String, Object> cfg) {
        if (op == null || !"export.download".equals(op.name)) {
            return;
        }
        String start = first(in, cfg, "start");
        String end = first(in, cfg, "end");
        if (start == null || end == null) {
            return;
        }
        if (!AMPLITUDE_EXPORT_HOUR.matcher(start).matches() || !AMPLITUDE_EXPORT_HOUR.matcher(end).matches()) {
            throw invalidInput("start and end must be YYYYMMDDTHH (UTC hour)");
        }
        LocalDateTime startAt;
        LocalDateTime endAt;
        try {
            startAt = LocalDateTime.parse(start, AMPLITUDE_EXPORT_HOUR_FMT);
            endAt = LocalDateTime.parse(end, AMPLITUDE_EXPORT_HOUR_FMT);
        } catch (DateTimeParseException e) {
            throw invalidInput("start and end must be YYYYMMDDTHH (UTC hour)");
        }
        if (endAt.isBefore(startAt)) {
            throw invalidInput("end must be the same hour as start or later");
        }
        if (ChronoUnit.HOURS.between(startAt, endAt) > AMPLITUDE_EXPORT_MAX_HOURS) {
            throw invalidInput("export span must be at most 365 days");
        }
    }

    private static Map<String, Object> stringKeyMap(Map<?, ?> map) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() != null) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    private static boolean blank(Map<String, Object> m, String key) {
        if (m != null) {
            Object v = m.get(key);
            if (v instanceof Number) {
                return false;
            }
            if (v instanceof Collection<?> c && c.isEmpty()) {
                return true;
            }
            if (v != null && v.getClass().isArray() && java.lang.reflect.Array.getLength(v) == 0) {
                return true;
            }
        }
        return DataSupport.cfgStr(m, key, null) == null && DataSupport.inStr(m, key, null) == null;
    }
}
