package org.pahappa.systems.aiquery.agent;

import org.pahappa.systems.aiquery.client.AiChatClient;
import org.pahappa.systems.aiquery.client.ChatCompletionResult;
import org.pahappa.systems.aiquery.client.ChatMessage;
import org.pahappa.systems.aiquery.client.ToolCall;
import org.pahappa.systems.aiquery.client.ToolDefinition;
import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.metadata.ResolvedEntity;
import org.pahappa.systems.aiquery.service.AiQuerySchemaDescriber;
import org.sers.webutils.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Runs the iterative "question -> call query_db -> see rows -> maybe call again -> answer" loop:
 * the model gets to see real data before committing to a final plan, instead of {@link
 * org.pahappa.systems.aiquery.service.NaturalLanguageQueryTranslator}'s single blind guess.
 * {@link QueryDbTool} independently re-validates and audit-logs every single call this loop
 * makes -- there is no fast path here that skips {@code AiQueryValidator}. The iteration cap is
 * counted here in Java and never delegated to the model's own judgment about when to stop.
 */
@Service
public class AgenticQueryOrchestrator {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgenticQueryOrchestrator.class);

    private static final String INSTRUCTIONS =
            "You answer a user's natural-language question against the read-only schema below, " +
            "by calling the query_db tool. You never invent an entity or field name that is not " +
            "listed. Use today's date, given above, to turn relative date references such as " +
            "\"this year\" or \"last 30 days\" into concrete filter values.\n\n" +
            "Call query_db as many times as you need -- to explore what values a field actually " +
            "holds, to correct a plan after a failed attempt, or to check a hunch -- before " +
            "answering. For a \"which X has the most/highest/least/lowest Y\" question, sort by " +
            "the relevant field (DESC or ASC) with a small page size rather than guessing an " +
            "answer from an unsorted page. Once you have the data you need, reply in plain text " +
            "with no further tool call. If the schema genuinely has nothing that can answer the " +
            "question, say so in plain text instead of calling the tool.";

    private final AiChatClient aiChatClient;
    private final QueryDbTool queryDbTool;
    private final AiQuerySchemaDescriber schemaDescriber;
    private final AiQueryProperties properties;

    @Autowired
    public AgenticQueryOrchestrator(AiChatClient aiChatClient, QueryDbTool queryDbTool,
                                     AiQuerySchemaDescriber schemaDescriber, AiQueryProperties properties) {
        this.aiChatClient = aiChatClient;
        this.queryDbTool = queryDbTool;
        this.schemaDescriber = schemaDescriber;
        this.properties = properties;
    }

    /**
     * @throws AiQueryValidationException if no query ever succeeded -- either the model itself
     * explained why the question can't be answered, or the iteration cap was hit with nothing to
     * show for it. Callers already catch this exception for the single-shot path and can route it
     * to the same advisory-chat fallback unchanged.
     */
    public AgenticQueryOutcome run(User user, String naturalLanguageQuery) {
        if (naturalLanguageQuery == null || naturalLanguageQuery.trim().isEmpty()) {
            throw new AiQueryValidationException("A question is required.");
        }
        List<ResolvedEntity> accessibleEntities = schemaDescriber.listAccessibleEntities(user);
        if (accessibleEntities.isEmpty()) {
            throw new AiQueryValidationException("No queryable data is available for this user.");
        }
        String schema = schemaDescriber.describeSchema(user, accessibleEntities);
        if (schema.isEmpty()) {
            throw new AiQueryValidationException("The question could not be matched to any queryable data.");
        }

        String systemPrompt = "Today's date is " + LocalDate.now() + " (yyyy-MM-dd).\n\n" + schema + "\n" + INSTRUCTIONS;
        List<ToolDefinition> tools = Collections.singletonList(queryDbTool.definition());

        List<ChatMessage> messages = new ArrayList<ChatMessage>();
        messages.add(ChatMessage.system(systemPrompt));
        messages.add(ChatMessage.user(naturalLanguageQuery));

        int executedCount = 0;
        ToolExecutionResult lastSuccess = null;
        boolean capExhausted = false;

        while (true) {
            ChatCompletionResult response = aiChatClient.generateWithTools(messages, tools);

            if (!response.hasToolCalls()) {
                if (lastSuccess != null) {
                    return new AgenticQueryOutcome(lastSuccess.plan(), lastSuccess.queryResult(), false);
                }
                LOGGER.warn("Agentic loop for question '{}' ended with no successful query. Model's reply: {}",
                        naturalLanguageQuery, response.content());
                throw new AiQueryValidationException(response.content() != null
                        ? response.content()
                        : "The question could not be matched to any queryable data.");
            }

            messages.add(ChatMessage.assistant(response.content(), response.toolCalls()));

            for (ToolCall toolCall : response.toolCalls()) {
                if (executedCount >= properties.getAgentMaxIterations()) {
                    capExhausted = true;
                    break;
                }
                executedCount++;
                ToolExecutionResult toolResult = queryDbTool.execute(user, toolCall.argumentsJson());
                messages.add(ChatMessage.tool(toolCall.id(), toolResult.toolMessageContent()));
                if (toolResult.success()) {
                    lastSuccess = toolResult;
                }
            }

            if (capExhausted) {
                break;
            }
        }

        // Cap hit: never ask the model again -- answer from the last successful result, or give up.
        if (lastSuccess != null) {
            LOGGER.warn("Agentic loop for question '{}' hit its {}-attempt cap; answering from the last successful result.",
                    naturalLanguageQuery, properties.getAgentMaxIterations());
            return new AgenticQueryOutcome(lastSuccess.plan(), lastSuccess.queryResult(), true);
        }
        throw new AiQueryValidationException("The question could not be answered within the allowed number of attempts.");
    }
}
