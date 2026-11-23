package com.studioquack.nodelibrary.nodes.testemulator;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Instance configuration parameters (shortCode → value) matching
 * {@code node_test_emulator_definition.json}: timeout, max log chars, verbose,
 * allowed languages.
 *
 * <p>Values come from {@code executionContext.currentNode.data.configurationParameters}
 * and may be overridden per-test by the INPUT field {@code configuration}.
 */
public final class EmulatorConfig {

    public static final int DEFAULT_TIMEOUT_SECONDS = 60;
    public static final int DEFAULT_MAX_LOG_CHARS = 20_000;
    public static final String DEFAULT_ALLOWED = "python,javascript,typescript,java";

    private final int defaultTimeoutSeconds;
    private final int maxLogChars;
    private final boolean verbose;
    private final Set<String> allowedLanguages;

    public EmulatorConfig(int defaultTimeoutSeconds, int maxLogChars, boolean verbose,
                          Set<String> allowedLanguages) {
        this.defaultTimeoutSeconds = defaultTimeoutSeconds > 0 ? defaultTimeoutSeconds : DEFAULT_TIMEOUT_SECONDS;
        this.maxLogChars = maxLogChars > 0 ? maxLogChars : DEFAULT_MAX_LOG_CHARS;
        this.verbose = verbose;
        this.allowedLanguages = allowedLanguages == null || allowedLanguages.isEmpty()
                ? parseAllowed(DEFAULT_ALLOWED) : Set.copyOf(allowedLanguages);
    }

    /** Defaults only — used when the invoke body has no configurationParameters. */
    public static EmulatorConfig defaults() {
        return new EmulatorConfig(DEFAULT_TIMEOUT_SECONDS, DEFAULT_MAX_LOG_CHARS, false,
                parseAllowed(DEFAULT_ALLOWED));
    }

    /**
     * Merge node-instance params with the per-test {@code configuration} object
     * (test overrides win).
     */
    public static EmulatorConfig from(Map<String, Object> nodeParams, Map<String, Object> testOverrides) {
        Map<String, Object> merged = EmulatorUtil.mutableMap(nodeParams);
        if (testOverrides != null) {
            merged.putAll(testOverrides);
        }
        return new EmulatorConfig(
                EmulatorUtil.toInt(merged.get("default_timeout_seconds"), DEFAULT_TIMEOUT_SECONDS),
                EmulatorUtil.toInt(merged.get("max_log_chars"), DEFAULT_MAX_LOG_CHARS),
                EmulatorUtil.isTruthy(merged.getOrDefault("verbose", false)),
                parseAllowed(EmulatorUtil.str(merged.getOrDefault("allowed_languages", DEFAULT_ALLOWED))));
    }

    /** Read {@code configurationParameters[]} from the invoke executionContext. */
    public static Map<String, Object> readNodeParams(Map<String, Object> invokeRequest) {
        Object list = EmulatorUtil.dig(invokeRequest, "executionContext", "currentNode", "data",
                "configurationParameters");
        Map<String, Object> out = new java.util.LinkedHashMap<>();
        if (!(list instanceof List<?> params)) {
            return out;
        }
        for (Object item : params) {
            if (!(item instanceof Map<?, ?> p)) {
                continue;
            }
            String shortCode = EmulatorUtil.str(p.get("shortCode"));
            Object value = p.get("value");
            if (!shortCode.isBlank() && value != null) {
                out.put(shortCode, value);
            }
        }
        return out;
    }

    static Set<String> parseAllowed(String csv) {
        if (csv == null || csv.isBlank()) {
            return parseAllowed(DEFAULT_ALLOWED);
        }
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .map(s -> s.toLowerCase(java.util.Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public int defaultTimeoutSeconds() {
        return defaultTimeoutSeconds;
    }

    public int maxLogChars() {
        return maxLogChars;
    }

    public boolean verbose() {
        return verbose;
    }

    public Set<String> allowedLanguages() {
        return allowedLanguages;
    }
}
