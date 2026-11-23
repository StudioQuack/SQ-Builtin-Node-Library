package studioquack;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Typed business error thrown from a node handler.
 *
 * <p>The dispatcher folds this into {@code helper.fail(errorCode, message, data)}
 * so the daemon raises {@code BpmnError(code)} and routes to the node's error
 * boundary. Do not put an {@code "error"} key on an OUTPUT port — the next node
 * would consume it as a message.
 */
public class StudioQuackError extends RuntimeException {

    private final String errorCode;
    private final Map<String, Object> data;

    /**
     * @param errorCode required non-blank BPMN error code (must match the boundary)
     * @param message   redacted detail for the boundary handler
     */
    public StudioQuackError(String errorCode, String message) {
        this(errorCode, message, null);
    }

    /**
     * @param errorCode required non-blank BPMN error code
     * @param message   redacted detail
     * @param data      optional extra map posted on {@code /v1/helper/fail}
     */
    public StudioQuackError(String errorCode, String message, Map<String, Object> data) {
        super(message == null ? "" : message);
        if (errorCode == null || errorCode.isBlank()) {
            throw new IllegalArgumentException("errorCode is required");
        }
        this.errorCode = errorCode;
        this.data = data == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(data));
    }

    /** BPMN error code the boundary must match. */
    public String errorCode() {
        return errorCode;
    }

    /** Extra fail payload; never {@code null}. */
    public Map<String, Object> data() {
        return data;
    }
}
