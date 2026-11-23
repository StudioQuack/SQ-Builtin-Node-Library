package com.studioquack.nodelibrary.nodes.testemulator;

import com.studioquack.nodelibrary.core.NodeHandler;
import studioquack.StudioQuackHelper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Node Test Emulator handler of the SQ Builtin Node Library image.
 *
 * <p>Self node is still Node Test Emulator. The host app owns dispatch.
 * The daemon POSTs one JSON invoke body per test. Reply keys match the
 * OUTPUT ports: {@code output} (test report) and {@code error} (empty when ok).
 */
public final class TestEmulatorNode implements NodeHandler {

    private final ScriptTestRunner scripts = new ScriptTestRunner();
    private final BuildTestRunner builds = new BuildTestRunner();
    private final String templateId;

    /**
     * Library template id for this node. MUST match
     * {@code node_test_emulator_definition.json} {@code _id} unless
     * {@code SQ_TEMPLATE_ID_NODE_TEST_EMULATOR} overrides it.
     */
    static final String SELF_TEMPLATE_ID = "17dcc655-08e2-4b45-b3a0-08d5aa608aed";

    public TestEmulatorNode() {
        this(SELF_TEMPLATE_ID);
    }

    public TestEmulatorNode(String templateId) {
        this.templateId = (templateId == null || templateId.isBlank())
                ? SELF_TEMPLATE_ID
                : templateId;
    }

    @Override
    public String templateId() {
        return templateId;
    }

    /**
     * Self-node invoke. {@code _helper} is unused: log + build-mode stay on
     * {@link HelperClient} ({@code /v1/helper/test/build-run}), not {@link StudioQuackHelper}.
     * Failures return {@link #errorPorts} so the dispatcher does not wrap them as
     * {@code {"error":…}}. Never log {@code helperToken}.
     */
    @Override
    public Map<String, Object> handle(Map<String, Object> req, StudioQuackHelper _helper) {
        HelperClient helper = new HelperClient(null);
        try {
            if (req == null) {
                return errorPorts("request body must be a JSON object");
            }
            helper = new HelperClient(EmulatorUtil.str(req.get("helperToken")));
            Map<String, Object> nodeParams = EmulatorConfig.readNodeParams(req);
            // Per-test configuration is inside the input port; parse a first-pass config
            // from node params only, then re-merge after the input is parsed.
            EmulatorConfig config = EmulatorConfig.from(nodeParams, Map.of());
            EmulatorInput input;
            try {
                input = EmulatorInput.parse(req, config);
            } catch (IllegalArgumentException e) {
                helper.log("emulator rejected input: " + e.getMessage());
                return errorPorts(e.getMessage());
            }
            config = EmulatorConfig.from(nodeParams, input.configuration());
            helper.log(Map.of(
                    "message", "node-test-emulator start",
                    "language", input.language(),
                    "mode", input.mode(),
                    "requestId", EmulatorUtil.str(req.get("requestId"))));

            TestReport report = "build".equals(input.mode())
                    ? builds.run(input, config, req)
                    : scripts.run(input, config);

            Map<String, Object> ports = new LinkedHashMap<>();
            ports.put("output", report.toMap(config.maxLogChars()));
            // OUTPUT error is the emulator-level channel. A completed test (pass or fail)
            // always leaves it empty — failures live in output.error / test_passed.
            ports.put("error", "");
            helper.log(Map.of(
                    "message", "node-test-emulator done",
                    "test_passed", report.testPassed(),
                    "requestId", EmulatorUtil.str(req.get("requestId"))));
            return ports;
        } catch (Exception e) {
            helper.log("emulator internal: " + e.getClass().getSimpleName());
            return errorPorts(
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    /** OUTPUT ports populated when the emulator cannot even start the test. */
    static Map<String, Object> errorPorts(String message) {
        TestReport report = new TestReport()
                .scriptSuccess(false)
                .error(message)
                .compareExpected(null)
                .finish();
        Map<String, Object> ports = new LinkedHashMap<>();
        ports.put("output", report.toMap(EmulatorConfig.DEFAULT_MAX_LOG_CHARS));
        ports.put("error", message == null ? "" : message);
        return ports;
    }
}
