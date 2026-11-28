package studioquack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Local execution-context accessors (Python {@code StudioQuackHelper} context surface).
 *
 * <p>{@link StudioQuackHelper} extends this class so node code calls
 * {@code helper.getInput("in")} etc. All methods read the in-memory map only — they
 * never touch the helper channel. Side effects live on {@link StudioQuackHelper}.
 */
public class ExecutionContextStore {

    /** Mutable execution-context map (shallow-copied on construct / set). */
    protected Map<String, Object> executionContext;

    /**
     * @param executionContext decoded context; {@code null} becomes an empty map
     */
    protected ExecutionContextStore(Map<String, Object> executionContext) {
        this.executionContext = copyMap(executionContext);
    }

    /**
     * Snapshot of the execution-context map held by this helper.
     *
     * <p>Use this when you need the whole blob (ids, nodes, current node). Prefer the
     * typed accessors below for ports and parameters.
     *
     * <pre>{@code
     * String runId = Json.str(helper.getExecutionContext().get("workflowExecutionId"));
     * }</pre>
     */
    public Map<String, Object> getExecutionContext() {
        return executionContext;
    }

    /**
     * Replace the in-memory execution context (local only — does not POST).
     *
     * <p>Use this for tests or after you built a patched copy. Call
     * {@link StudioQuackHelper#returnExecutionContext()} to publish.
     *
     * <pre>{@code
     * helper.setExecutionContext(patched);
     * }</pre>
     */
    public Map<String, Object> setExecutionContext(Map<String, Object> ctx) {
        this.executionContext = copyMap(ctx);
        return this.executionContext;
    }

    /**
     * Merge keys into the in-memory execution context (local only).
     *
     * <pre>{@code
     * helper.updateExecutionContext(Map.of("note", "patched"));
     * }</pre>
     */
    public Map<String, Object> updateExecutionContext(Map<String, Object> patch) {
        if (patch != null) {
            executionContext.putAll(patch);
        }
        return executionContext;
    }

    /**
     * Set one top-level context key locally.
     *
     * <pre>{@code
     * helper.setExecutionContextValue("jobKey", "demo");
     * }</pre>
     */
    public Map<String, Object> setExecutionContextValue(String key, Object value) {
        if (key != null && !key.isBlank()) {
            executionContext.put(key, value);
        }
        return executionContext;
    }

    /**
     * Write an INPUT port value on the current node (local context only).
     *
     * <p>Use this method to set the value of port {@code in} before a transform:
     *
     * <pre>{@code
     * helper.updateInput("in", Map.of("a", 3, "b", 5));
     * }</pre>
     */
    public Map<String, Object> updateInput(String portValue, Object value) {
        Map<String, Object> node = currentNodeMutable();
        if (node == null || portValue == null) {
            return executionContext;
        }
        List<Object> inputList = Json.asList(node.get("inputList"));
        node.put("inputList", inputList);
        for (Object entryObj : inputList) {
            Map<String, Object> entry = Json.asMap(entryObj);
            if (portValue.equals(Json.str(entry.get("targetPortValue")))) {
                entry.put("value", value);
                return executionContext;
            }
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("targetPortValue", portValue);
        created.put("value", value);
        inputList.add(created);
        return executionContext;
    }

    /**
     * Write an OUTPUT port value on the current node (local context only).
     *
     * <p>Use this method to set the value of port {@code sum} in-memory. To deliver it
     * to the workflow, also call {@link StudioQuackHelper#returnResult(String, Object)}.
     *
     * <pre>{@code
     * helper.setOutput("sum", 8);
     * }</pre>
     */
    public Map<String, Object> setOutput(String portValue, Object value) {
        Map<String, Object> node = currentNodeMutable();
        if (node == null || portValue == null) {
            return executionContext;
        }
        List<Object> outputList = Json.asList(node.get("outputList"));
        node.put("outputList", outputList);
        for (Object entryObj : outputList) {
            Map<String, Object> entry = Json.asMap(entryObj);
            if (portValue.equals(Json.str(entry.get("portValue")))) {
                entry.put("value", value);
                return executionContext;
            }
        }
        Map<String, Object> created = new LinkedHashMap<>();
        created.put("portValue", portValue);
        created.put("value", value);
        outputList.add(created);
        return executionContext;
    }

    /**
     * Workflow run id from the context.
     *
     * <pre>{@code
     * String id = helper.getWorkflowExecutionId();
     * }</pre>
     */
    public String getWorkflowExecutionId() {
        return Json.str(executionContext.get("workflowExecutionId"));
    }

    /**
     * Workflow definition id from the context ({@code workflowId}).
     */
    public String getWorkflowId() {
        return Json.str(executionContext.get("workflowId"));
    }

    /**
     * Conversation id from {@code agenticInfo.conversationId} (falling back to top-level
     * {@code conversationId}). Empty string when this run is not attached to a conversation.
     */
    public String getConversationId() {
        Map<String, Object> agentic = Json.asMap(executionContext.get("agenticInfo"));
        String conv = Json.str(agentic.get("conversationId")).trim();
        if (!conv.isEmpty()) {
            return conv;
        }
        return Json.str(executionContext.get("conversationId")).trim();
    }

    /**
     * Current node object ({@code currentNode}).
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; node = helper.getCurrentNode();
     * }</pre>
     */
    public Map<String, Object> getCurrentNode() {
        return Json.asMap(executionContext.get("currentNode"));
    }

    /**
     * Current node id ({@code currentNode.id}, else {@code currentNodeId}).
     *
     * <pre>{@code
     * String nodeId = helper.getCurrentNodeId();
     * }</pre>
     */
    public String getCurrentNodeId() {
        Object id = getCurrentNode().get("id");
        if (id == null) {
            id = executionContext.get("currentNodeId");
        }
        return Json.str(id);
    }

    /**
     * Edge-transform list on the current node (DuckTape configs).
     *
     * <pre>{@code
     * List&lt;Object&gt; edges = helper.getEdgeTransformListForCurrentNode();
     * }</pre>
     */
    public List<Object> getEdgeTransformListForCurrentNode() {
        return Json.asList(getCurrentNode().get("edgeTransformListForCurrentNode"));
    }

    /**
     * Full INPUT list ({@code currentNode.inputList}).
     *
     * <p>Use {@link #getInput(String)} / {@link #getInputValuesByPortValue} to read one port.
     *
     * <pre>{@code
     * List&lt;Object&gt; inputs = helper.getInput();
     * }</pre>
     */
    public List<Object> getInput() {
        return Json.asList(getCurrentNode().get("inputList"));
    }

    /**
     * First value of one INPUT port (unwrap of {@link #getInputValuesByPortValue}).
     *
     * <p>Use this method to read the value of port {@code in}:
     *
     * <pre>{@code
     * Object in = helper.getInput("in"); // {"a":3,"b":5} or a scalar
     * }</pre>
     *
     * @param portValue INPUT port name ({@code targetPortValue})
     */
    public Object getInput(String portValue) {
        List<Object> values = getInputValuesByPortValue(portValue, null);
        return values.isEmpty() ? null : values.get(0);
    }

    /**
     * All values bound to one INPUT port. Falls back to schema defaults when empty.
     *
     * <p>Use this method to read the value of port {@code in}:
     *
     * <pre>{@code
     * List&lt;Object&gt; ins = helper.getInputValuesByPortValue("in");
     * Map&lt;String, Object&gt; first = Json.asMap(ins.isEmpty() ? null : ins.get(0));
     * }</pre>
     *
     * @param portValue INPUT port name
     */
    public List<Object> getInputValuesByPortValue(String portValue) {
        return getInputValuesByPortValue(portValue, null);
    }

    /**
     * {@link #getInputValuesByPortValue(String)} filtered by source {@code nodeId}.
     *
     * <p>Use this method to read the value of port {@code in} coming from a specific node.
     *
     * @param nodeId optional source node id; {@code null} = any
     */
    public List<Object> getInputValuesByPortValue(String portValue, String nodeId) {
        List<Object> result = new ArrayList<>();
        for (Object itemObj : getInput()) {
            Map<String, Object> item = Json.asMap(itemObj);
            if (item.isEmpty()) {
                continue;
            }
            if (!String.valueOf(item.get("targetPortValue")).equals(portValue)) {
                continue;
            }
            if (nodeId != null && !nodeId.equals(item.get("nodeId"))) {
                continue;
            }
            result.add(item.get("value"));
        }
        if (result.isEmpty()) {
            return setDefaultInputValuesByPortValue(portValue);
        }
        return result;
    }

    /**
     * Default object for an object-schema INPUT port (children shortCodes → {@code null}).
     *
     * <p>Use this method to read the schema defaults of port {@code in} when nothing is wired.
     *
     * <pre>{@code
     * List&lt;Object&gt; defaults = helper.setDefaultInputValuesByPortValue("in");
     * }</pre>
     */
    public List<Object> setDefaultInputValuesByPortValue(String portValue) {
        Map<String, Object> schema = getPortSchemaByName(portValue);
        Map<String, Object> def = new LinkedHashMap<>();
        for (Object childObj : Json.asList(schema.get("children"))) {
            Map<String, Object> child = Json.asMap(childObj);
            Object shortCode = child.get("shortCode");
            if (shortCode != null) {
                def.put(String.valueOf(shortCode), null);
            }
        }
        return def.isEmpty() ? List.of() : List.of(def);
    }

    /**
     * OUTPUT list on the current node.
     *
     * <p>Use this method to read the value of port {@code sum} after {@link #setOutput}:
     *
     * <pre>{@code
     * List&lt;Object&gt; outs = helper.getOutput();
     * }</pre>
     */
    public List<Object> getOutput() {
        return Json.asList(getCurrentNode().get("outputList"));
    }

    /**
     * Output-port specs on the current node ({@code specListForNodeOutput}).
     *
     * <pre>{@code
     * List&lt;Object&gt; specs = helper.getSpecListForNodeOutput();
     * }</pre>
     */
    public List<Object> getSpecListForNodeOutput() {
        return Json.asList(getCurrentNode().get("specListForNodeOutput"));
    }

    /**
     * Current node's template payload ({@code currentNode.data}).
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; tpl = helper.getTemplate();
     * }</pre>
     */
    public Map<String, Object> getTemplate() {
        return Json.asMap(getCurrentNode().get("data"));
    }

    /**
     * Port descriptor whose {@code value} field equals {@code portValue}.
     *
     * <p>Use this method to read the definition of port {@code in}:
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; port = helper.getPortByValue("in");
     * }</pre>
     */
    public Map<String, Object> getPortByValue(String portValue) {
        for (Object portObj : Json.asList(getTemplate().get("ports"))) {
            Map<String, Object> port = Json.asMap(portObj);
            if (String.valueOf(port.get("value")).equals(portValue)) {
                return port;
            }
        }
        return null;
    }

    /**
     * {@code value} field of the named port (usually the port name itself).
     *
     * <p>Use this method to read the value of port {@code in}'s descriptor:
     *
     * <pre>{@code
     * Object v = helper.getPortValueByName("in");
     * }</pre>
     */
    public Object getPortValueByName(String portValue) {
        Map<String, Object> port = getPortByValue(portValue);
        return port == null ? Map.of() : port.getOrDefault("value", Map.of());
    }

    /**
     * JSON schema of one port ({@code schema} field, parsed if it is a string).
     *
     * <p>Use this method to read the schema of port {@code in}:
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; schema = helper.getPortSchemaByName("in");
     * }</pre>
     */
    public Map<String, Object> getPortSchemaByName(String portValue) {
        Map<String, Object> port = getPortByValue(portValue);
        if (port == null) {
            return new LinkedHashMap<>();
        }
        Object schema = port.get("schema");
        if (schema instanceof String s) {
            return Json.asMap(Json.parse(s));
        }
        return Json.asMap(schema);
    }

    /**
     * Node configuration parameters ({@code data.configurationParameters}).
     *
     * <p>Use this to read {@code runtime_mode} via {@link #getParameterValueByShortCode}.
     *
     * <pre>{@code
     * List&lt;Object&gt; params = helper.getParameters();
     * }</pre>
     */
    public List<Object> getParameters() {
        return Json.asList(getTemplate().get("configurationParameters"));
    }

    /**
     * One configuration parameter by {@code shortCode}.
     *
     * <p>Use this to read the {@code runtime_mode} parameter object:
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; p = helper.getParameterByShortCode("runtime_mode");
     * }</pre>
     */
    public Map<String, Object> getParameterByShortCode(String shortCode) {
        for (Object paramObj : getParameters()) {
            Map<String, Object> param = Json.asMap(paramObj);
            if (String.valueOf(param.get("shortCode")).equals(shortCode)) {
                return param;
            }
        }
        return null;
    }

    /**
     * Value of a configuration parameter by {@code shortCode}.
     *
     * <p>Use this to read {@code runtime_mode} ({@code "stateless"} | {@code "stateful"}):
     *
     * <pre>{@code
     * Object mode = helper.getParameterValueByShortCode("runtime_mode", "stateless");
     * }</pre>
     */
    public Object getParameterValueByShortCode(String shortCode, Object returnValue) {
        Map<String, Object> param = getParameterByShortCode(shortCode);
        if (param != null) {
            return param.getOrDefault("value", Map.of());
        }
        return returnValue;
    }

    /** {@link #getParameterValueByShortCode(String, Object)} with {@code null} default. */
    public Object getParameterValueByShortCode(String shortCode) {
        return getParameterValueByShortCode(shortCode, null);
    }

    /**
     * Connection list on the current node.
     *
     * <pre>{@code
     * List&lt;Object&gt; conns = helper.getConnections();
     * }</pre>
     */
    public List<Object> getConnections() {
        return Json.asList(getCurrentNode().get("connectionList"));
    }

    /**
     * First connection whose {@code portValue} matches.
     *
     * <p>Use this method to read connections on FUNCTION port {@code log}:
     *
     * <pre>{@code
     * Map&lt;String, Object&gt; c = helper.findConnectionByPortValue("log");
     * }</pre>
     */
    public Map<String, Object> findConnectionByPortValue(String portValue) {
        for (Object connObj : getConnections()) {
            Map<String, Object> conn = Json.asMap(connObj);
            if (String.valueOf(conn.get("portValue")).equals(portValue)) {
                return conn;
            }
        }
        return null;
    }

    /**
     * {@code connectionDetailList} for one port.
     *
     * <p>Use this method to read connection details of port {@code log}:
     *
     * <pre>{@code
     * List&lt;Object&gt; details = helper.getConnectionDetailListByPortValue("log");
     * }</pre>
     */
    public List<Object> getConnectionDetailListByPortValue(String portValue) {
        Map<String, Object> conn = findConnectionByPortValue(portValue);
        return conn == null ? List.of() : Json.asList(conn.get("connectionDetailList"));
    }

    /**
     * First connected node id on a port.
     *
     * <p>Use this method to find who is wired to port {@code log}:
     *
     * <pre>{@code
     * Object nodeId = helper.findFirstConnectionNodeIdByPortValue("log");
     * }</pre>
     */
    public Object findFirstConnectionNodeIdByPortValue(String portValue) {
        List<Object> details = getConnectionDetailListByPortValue(portValue);
        if (details.isEmpty()) {
            return null;
        }
        return Json.asMap(details.get(0)).get("nodeId");
    }

    /**
     * DuckTape (edge-transform) rows between this node and {@code nodeId}.
     *
     * <p>Use this method to read transforms from port {@code sum} to a downstream port:
     *
     * <pre>{@code
     * List&lt;Object&gt; cfgs = helper.getDucktapeConfigurations(otherId, "sum", "in");
     * }</pre>
     */
    public List<Object> getDucktapeConfigurations(String nodeId, String sourcePortName, String targetPortName) {
        List<Object> out = new ArrayList<>();
        String currentId = getCurrentNodeId();
        for (Object cfgObj : getEdgeTransformListForCurrentNode()) {
            Map<String, Object> cfg = Json.asMap(cfgObj);
            Object srcN = cfg.get("sourceNodeId");
            Object srcP = cfg.get("sourcePortShortCode");
            Object tgtN = cfg.get("targetNodeId");
            Object tgtP = cfg.get("targetPortShortCode");
            boolean forward = currentId.equals(Json.str(srcN)) && sourcePortName.equals(Json.str(srcP))
                    && Json.str(nodeId).equals(Json.str(tgtN)) && targetPortName.equals(Json.str(tgtP));
            boolean back = Json.str(nodeId).equals(Json.str(srcN)) && targetPortName.equals(Json.str(srcP))
                    && currentId.equals(Json.str(tgtN)) && sourcePortName.equals(Json.str(tgtP));
            if (forward || back) {
                out.add(cfg);
            }
        }
        return out;
    }

    /**
     * {@code configurationParameters} on one connection-detail row.
     *
     * <pre>{@code
     * List&lt;Object&gt; cps = helper.getConnectionDetailParameters(detail);
     * }</pre>
     */
    public List<Object> getConnectionDetailParameters(Map<String, Object> connectionDetail) {
        return Json.asList(connectionDetail == null ? null : connectionDetail.get("configurationParameters"));
    }

    /**
     * One connection-detail parameter value by {@code shortCode}.
     *
     * <pre>{@code
     * Object v = helper.getConnectionDetailParameterByShortCode(cps, "timeout");
     * }</pre>
     */
    public Object getConnectionDetailParameterByShortCode(List<?> configurationParameters, String shortCode) {
        if (configurationParameters == null) {
            return null;
        }
        for (Object cpObj : configurationParameters) {
            Map<String, Object> cp = Json.asMap(cpObj);
            if (String.valueOf(cp.get("shortCode")).equals(shortCode)) {
                return cp.get("value");
            }
        }
        return null;
    }

    /** Live {@code currentNode} map, or {@code null} when absent. */
    protected Map<String, Object> currentNodeMutable() {
        Object node = executionContext.get("currentNode");
        if (node instanceof Map<?, ?>) {
            Map<String, Object> live = Json.asMap(node);
            executionContext.put("currentNode", live);
            return live;
        }
        return null;
    }

    /** Shallow copy; never returns {@code null}. */
    protected static Map<String, Object> copyMap(Map<String, Object> src) {
        return src == null ? new LinkedHashMap<>() : new LinkedHashMap<>(src);
    }
}
