package com.studioquack.nodelibrary.core;

import org.junit.jupiter.api.Test;
import studioquack.LlmHelper;
import studioquack.StudioQuackHelper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Java helper exposes the same method surface as the Python and JavaScript / TypeScript
 * sandbox helpers.
 * Java streams with {@code executeStream} and emits with {@code emitPartial}, like JS / TS.
 */
class HelperSurfaceParityTest {

    private static final List<String> SHARED = List.of(
            "getExecutionContext", "setExecutionContext", "updateExecutionContext", "setExecutionContextValue",
            "updateInput", "setOutput", "returnExecutionContext", "getWorkflowExecutionId", "getCurrentNode",
            "getCurrentNodeId", "getEdgeTransformListForCurrentNode", "getInput", "getInputValuesByPortValue",
            "setDefaultInputValuesByPortValue", "getOutput", "getSpecListForNodeOutput", "getTemplate",
            "getPortByValue", "getPortValueByName", "getPortSchemaByName", "getParameters",
            "getParameterByShortCode", "getParameterValueByShortCode", "getConnections",
            "findConnectionByPortValue", "getConnectionDetailListByPortValue",
            "findFirstConnectionNodeIdByPortValue", "getDucktapeConfigurations", "getConnectionDetailParameters",
            "getConnectionDetailParameterByShortCode", "getSecret", "fullContext", "currentNode",
            "currentNodeId", "workflowExecutionId", "parameters", "connections", "inputs", "input",
            "execute", "connect", "engineIP", "llmLocal", "returnResult", "fail", "escalate", "sendSignal",
            "sendMessage", "metric", "log", "isCancelled", "onCancel", "handleCancel", "isCancelPath",
            "setRequestContext",
            "executeStream", "emitPartial", "llm",
            "isWebhookInvocation", "getWebhookRequest", "getWebhookPayload", "getWebhookHeader",
            "verifyHmacSha256", "respondWebhookChallenge", "emitWebhookEvents", "rejectWebhook",
            "setCorrelationKey");

    private static final List<String> LLM = List.of(
            "completion", "chat", "local", "models", "load", "unload", "download", "downloadStatus");

    @Test
    void javaHelper_definesTheSharedSurface() {
        Set<String> missing = new TreeSet<>();
        Set<String> helper = publicMethods(StudioQuackHelper.class);
        for (String name : SHARED) {
            if (!helper.contains(name)) {
                missing.add(name);
            }
        }
        Set<String> llm = publicMethods(LlmHelper.class);
        for (String name : LLM) {
            if (!llm.contains(name)) {
                missing.add("llm()." + name);
            }
        }
        assertTrue(missing.isEmpty(), "Java helper is missing " + missing);
    }

    private static Set<String> publicMethods(Class<?> type) {
        List<Method> methods = new ArrayList<>(Arrays.asList(type.getMethods()));
        return methods.stream()
                .filter(m -> Modifier.isPublic(m.getModifiers()))
                .map(Method::getName)
                .collect(Collectors.toSet());
    }
}
