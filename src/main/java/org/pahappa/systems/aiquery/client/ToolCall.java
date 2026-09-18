package org.pahappa.systems.aiquery.client;

/**
 * One {@code tool_calls} entry from an assistant turn (request side, when echoing it back as
 * conversation history) or parsed out of one (response side, from {@link ChatCompletionResult}).
 * {@code argumentsJson} is the raw, unparsed JSON text the model produced for the function's
 * arguments -- the tool itself is responsible for parsing and validating it.
 */
public final class ToolCall {

    private final String id;
    private final String functionName;
    private final String argumentsJson;

    public ToolCall(String id, String functionName, String argumentsJson) {
        this.id = id;
        this.functionName = functionName;
        this.argumentsJson = argumentsJson;
    }

    public String id() {
        return id;
    }

    public String functionName() {
        return functionName;
    }

    public String argumentsJson() {
        return argumentsJson;
    }
}
