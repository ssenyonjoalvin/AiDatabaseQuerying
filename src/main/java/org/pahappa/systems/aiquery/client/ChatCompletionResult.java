package org.pahappa.systems.aiquery.client;

import java.util.Collections;
import java.util.List;

/**
 * One assistant turn returned by {@link AiChatClient#generateWithTools(List, List)}: either a
 * plain-text reply ({@link #content()} non-null, {@link #toolCalls()} empty), or one or more
 * tool invocations the caller must execute and answer with {@link ChatMessage#tool(String, String)}
 * messages before asking the model to continue.
 */
public final class ChatCompletionResult {

    private final String content;
    private final List<ToolCall> toolCalls;

    public ChatCompletionResult(String content, List<ToolCall> toolCalls) {
        this.content = content;
        this.toolCalls = toolCalls == null ? Collections.<ToolCall>emptyList() : toolCalls;
    }

    /** Null when the assistant turn is tool calls only. */
    public String content() {
        return content;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
