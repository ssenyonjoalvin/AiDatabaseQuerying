package org.pahappa.systems.aiquery.client;

import java.util.Collections;
import java.util.List;

/**
 * One message in an OpenAI-compatible chat/completions conversation, as sent to {@link
 * AiChatClient#generateWithTools(List, List)}. Never serialized as an HTTP response itself --
 * only ever built by an in-process caller and turned into request JSON by {@link AiChatClient}.
 */
public final class ChatMessage {

    private final String role;
    private final String content;
    private final List<ToolCall> toolCalls;
    private final String toolCallId;

    private ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCalls = toolCalls;
        this.toolCallId = toolCallId;
    }

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, Collections.<ToolCall>emptyList(), null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, Collections.<ToolCall>emptyList(), null);
    }

    /** {@code content} may be null when the assistant turn is tool calls only. */
    public static ChatMessage assistant(String content, List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", content, toolCalls, null);
    }

    /** A tool result message; {@code toolCallId} must match the {@link ToolCall#id()} it answers. */
    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, Collections.<ToolCall>emptyList(), toolCallId);
    }

    public String role() {
        return role;
    }

    public String content() {
        return content;
    }

    public List<ToolCall> toolCalls() {
        return toolCalls;
    }

    public String toolCallId() {
        return toolCallId;
    }
}
