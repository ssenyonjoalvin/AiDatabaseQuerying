package org.pahappa.systems.aiquery.client;


import org.apache.http.Header;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Generation via any OpenAI-compatible chat/completions API (Alibaba DashScope/Qwen,
 * Google Gemini, OpenAI itself, etc).
 *
 * The provider is entirely a matter of configuration -- {@code ai.base-url}, {@code ai.api-key}
 * and {@code ai.model} (plus {@code ai.vision-model} for image questions) select which model
 * answers, with no code change required. For example:
 * <ul>
 *   <li>Qwen (DashScope): {@code ai.base-url=https://dashscope-intl.aliyuncs.com/compatible-mode/v1}</li>
 *   <li>Gemini: {@code ai.base-url=https://generativelanguage.googleapis.com/v1beta/openai}</li>
 * </ul>
 */
@Service
public class AiChatClient {

    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";

    /**
     * Providers commonly return 429 for a transient per-minute token/request budget rather than
     * a hard quota exhaustion -- a single short wait-and-retry recovers most of these (the error
     * body itself usually names a sub-2s window before the budget resets), without the caller
     * needing to know anything about rate limits.
     */
    private static final int MAX_RATE_LIMIT_RETRIES = 1;
    private static final long DEFAULT_RATE_LIMIT_RETRY_DELAY_MS = 2000L;

    @Value("${ai.api-key:}") private String apiKey;
    @Value("${ai.base-url:}") private String baseUrl;
    @Value("${ai.model:}") private String model;
    @Value("${ai.vision-model:}") private String visionModel;

    private final ObjectMapper mapper = new ObjectMapper();
    private final CloseableHttpClient httpClient = HttpClients.createDefault();

    /** Answers a question given a system prompt, non-streaming. */
    public String generate(String systemPrompt, String userMessage) {
        requireConfigured();
        requireValue(model, "ai.model");
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);
            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userMessage);

            JsonNode root = post(body);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to call chat completions API: " + exception.getMessage(), exception);
        }
    }

    /** Answers a question about an inline image, via the same chat/completions endpoint. */
    public String generateWithImage(String systemPrompt, String userMessage, String imageDataUrl) {
        requireConfigured();
        requireValue(visionModel, "ai.vision-model");
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", visionModel);

            ArrayNode messages = body.putArray("messages");
            ObjectNode systemMessage = messages.addObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", systemPrompt);

            ObjectNode userMessageNode = messages.addObject();
            userMessageNode.put("role", "user");
            ArrayNode content = userMessageNode.putArray("content");
            content.addObject().put("type", "text").put("text", userMessage);
            content.addObject().put("type", "image_url").putObject("image_url").put("url", imageDataUrl);

            JsonNode root = post(body);
            return root.path("choices").path(0).path("message").path("content").asText();
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to call vision chat completions API: " + exception.getMessage(), exception);
        }
    }


    /**
     * Answers a question given a full conversation history and an optional set of tools the
     * model may call instead of answering directly. Non-streaming, same OpenAI-compatible
     * {@code /chat/completions} endpoint and 429-retry behavior as {@link #generate}. The caller
     * owns the message list -- nothing here remembers state between calls -- and is responsible
     * for appending the returned assistant turn (and any {@code role: "tool"} results) before
     * calling again.
     */
    public ChatCompletionResult generateWithTools(List<ChatMessage> messages, List<ToolDefinition> tools) {
        requireConfigured();
        requireValue(model, "ai.model");
        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", model);
            body.set("messages", toMessagesNode(messages));
            if (tools != null && !tools.isEmpty()) {
                body.set("tools", toToolsNode(tools));
                body.put("tool_choice", "auto");
            }

            try {
                JsonNode root = post(body);
                return toChatCompletionResult(root.path("choices").path(0).path("message"));
            } catch (ChatCompletionsHttpException httpException) {
                ChatCompletionResult recovered = recoverPlainTextFinalTurn(httpException, body);
                if (recovered != null) {
                    return recovered;
                }
                throw httpException;
            }
        } catch (ChatCompletionsHttpException httpException) {
            throw new IllegalStateException("Failed to call chat completions API: " + httpException.getMessage(), httpException);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to call chat completions API: " + exception.getMessage(), exception);
        }
    }

    /**
     * Some OpenAI-compatible providers (notably Groq {@code gpt-oss} models) reject a plain-text
     * assistant turn with HTTP 400 {@code output_parse_failed} when {@code tools} are still
     * attached, even though the model correctly decided it is done calling tools. Retry once
     * without tools, then salvage {@code failed_generation} from the error body if present.
     */
    private ChatCompletionResult recoverPlainTextFinalTurn(ChatCompletionsHttpException httpException, ObjectNode body)
            throws Exception {
        if (httpException.status != 400 || !httpException.responseBody.contains("output_parse_failed")) {
            return null;
        }
        if (body.has("tools")) {
            body.remove("tools");
            body.remove("tool_choice");
            try {
                JsonNode root = post(body);
                return toChatCompletionResult(root.path("choices").path(0).path("message"));
            } catch (ChatCompletionsHttpException retryException) {
                if (retryException.status != 400 || !retryException.responseBody.contains("output_parse_failed")) {
                    throw retryException;
                }
                return salvageFailedGeneration(retryException.responseBody);
            }
        }
        return salvageFailedGeneration(httpException.responseBody);
    }

    private ChatCompletionResult salvageFailedGeneration(String responseBody) {
        try {
            JsonNode errorNode = mapper.readTree(responseBody).path("error");
            if (!"output_parse_failed".equals(errorNode.path("code").asText())) {
                return null;
            }
            JsonNode failedNode = errorNode.path("failed_generation");
            String failedGeneration = failedNode.isMissingNode() || failedNode.isNull()
                    ? null : failedNode.asText();
            if (failedGeneration != null && !failedGeneration.trim().isEmpty()) {
                return new ChatCompletionResult(failedGeneration, Collections.<ToolCall>emptyList());
            }
        } catch (Exception ignored) {
            // not JSON or unexpected shape
        }
        return new ChatCompletionResult(null, Collections.<ToolCall>emptyList());
    }

    private ArrayNode toMessagesNode(List<ChatMessage> messages) {
        ArrayNode messagesNode = mapper.createArrayNode();
        for (ChatMessage message : messages) {
            ObjectNode messageNode = messagesNode.addObject();
            messageNode.put("role", message.role());
            if (message.content() != null) {
                messageNode.put("content", message.content());
            } else {
                messageNode.putNull("content");
            }
            if (message.toolCallId() != null) {
                messageNode.put("tool_call_id", message.toolCallId());
            }
            if (!message.toolCalls().isEmpty()) {
                ArrayNode toolCallsNode = messageNode.putArray("tool_calls");
                for (ToolCall toolCall : message.toolCalls()) {
                    ObjectNode toolCallNode = toolCallsNode.addObject();
                    toolCallNode.put("id", toolCall.id());
                    toolCallNode.put("type", "function");
                    ObjectNode functionNode = toolCallNode.putObject("function");
                    functionNode.put("name", toolCall.functionName());
                    functionNode.put("arguments", toolCall.argumentsJson());
                }
            }
        }
        return messagesNode;
    }

    private ArrayNode toToolsNode(List<ToolDefinition> tools) {
        ArrayNode toolsNode = mapper.createArrayNode();
        for (ToolDefinition tool : tools) {
            ObjectNode toolNode = toolsNode.addObject();
            toolNode.put("type", "function");
            ObjectNode functionNode = toolNode.putObject("function");
            functionNode.put("name", tool.name());
            functionNode.put("description", tool.description());
            functionNode.set("parameters", tool.parametersSchema());
        }
        return toolsNode;
    }

    private ChatCompletionResult toChatCompletionResult(JsonNode messageNode) {
        String content = messageNode.path("content").isMissingNode() || messageNode.path("content").isNull()
                ? null : messageNode.path("content").asText();
        List<ToolCall> toolCalls = new ArrayList<ToolCall>();
        for (JsonNode toolCallNode : messageNode.path("tool_calls")) {
            JsonNode functionNode = toolCallNode.path("function");
            toolCalls.add(new ToolCall(
                    toolCallNode.path("id").asText(),
                    functionNode.path("name").asText(),
                    functionNode.path("arguments").asText()));
        }
        return new ChatCompletionResult(content, toolCalls);
    }

    private JsonNode post(ObjectNode body) throws Exception {
        String json = mapper.writeValueAsString(body);
        for (int attempt = 0; ; attempt++) {
            HttpPost request = new HttpPost(baseUrl + CHAT_COMPLETIONS_PATH);
            request.setHeader("Authorization", "Bearer " + apiKey);
            request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));

            CloseableHttpResponse response = httpClient.execute(request);
            try {
                String responseBody = EntityUtils.toString(response.getEntity());
                int status = response.getStatusLine().getStatusCode();
                if (status == 429 && attempt < MAX_RATE_LIMIT_RETRIES) {
                    sleep(retryDelayMillis(response));
                    continue;
                }
                if (status != 200) {
                    throw new ChatCompletionsHttpException(status, responseBody);
                }
                return mapper.readTree(responseBody);
            } finally {
                response.close();
            }
        }
    }

    /** Honors the provider's Retry-After header when present, else a short fixed backoff. */
    private long retryDelayMillis(CloseableHttpResponse response) {
        Header header = response.getFirstHeader("Retry-After");
        if (header != null) {
            try {
                return Math.max(0L, Math.round(Double.parseDouble(header.getValue()) * 1000));
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_RATE_LIMIT_RETRY_DELAY_MS;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void requireConfigured() {
        requireValue(apiKey, "ai.api-key");
        requireValue(baseUrl, "ai.base-url");
    }

    private void requireValue(String value, String property) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Missing required property " + property + ". Set it in ai.local.properties "
                            + "(or wherever this host application supplies " + property + ").");
        }
    }

    private static final class ChatCompletionsHttpException extends Exception {
        private final int status;
        private final String responseBody;

        private ChatCompletionsHttpException(int status, String responseBody) {
            super("Chat completions API returned " + status + ": " + responseBody);
            this.status = status;
            this.responseBody = responseBody;
        }
    }

    @PreDestroy
    public void close() {
        try {
            httpClient.close();
        } catch (Exception ignored) {
            // best-effort cleanup
        }
    }
}
