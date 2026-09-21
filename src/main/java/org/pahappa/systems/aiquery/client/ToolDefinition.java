package org.pahappa.systems.aiquery.client;

import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * A single OpenAI-compatible {@code tools[].function} definition: a name the model refers to in
 * a {@link ToolCall}, a description, and a JSON Schema object describing its arguments.
 */
public final class ToolDefinition {

    private final String name;
    private final String description;
    private final ObjectNode parametersSchema;

    public ToolDefinition(String name, String description, ObjectNode parametersSchema) {
        this.name = name;
        this.description = description;
        this.parametersSchema = parametersSchema;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ObjectNode parametersSchema() {
        return parametersSchema;
    }
}
