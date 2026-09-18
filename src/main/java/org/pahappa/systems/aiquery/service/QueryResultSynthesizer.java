package org.pahappa.systems.aiquery.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.pahappa.systems.aiquery.client.AiChatClient;
import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.dto.QueryResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Turns a {@link QueryResult} plus the question that produced it into a natural-language
 * answer, by handing the model the actual returned rows and asking it to reason over them. This
 * runs strictly after {@link NaturalLanguageQueryTranslator} and query execution -- it never
 * touches the database or influences what was queried, so it can't be used to bypass
 * {@link org.pahappa.systems.aiquery.validation.AiQueryValidator}.
 */
@Service
public class QueryResultSynthesizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryResultSynthesizer.class);

    private final AiChatClient aiChatClient;
    private final AiQueryProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public QueryResultSynthesizer(AiChatClient aiChatClient, AiQueryProperties properties) {
        this.aiChatClient = aiChatClient;
        this.properties = properties;
    }

    /**
     * @return a natural-language answer grounded in {@code result}'s rows, or {@code null} if
     * synthesis failed -- callers should fall back to showing the raw {@link QueryResult} rather
     * than failing the whole request over it.
     */
    public String synthesize(String question, QueryResult result) {
        try {
            String systemPrompt = properties.getResultSynthesisSystemPrompt()
                    + "\n\nEntity queried: " + result.entity()
                    + "\nFields: " + result.fields()
                    + "\nReturned " + result.returnedCount() + " row(s)"
                    + (result.hasMore() ? " (more rows exist beyond this page)" : "")
                    + ".\nData (JSON array of rows):\n" + mapper.writeValueAsString(result.rows());
            return aiChatClient.generate(systemPrompt, question);
        } catch (Exception ex) {
            LOGGER.warn("Result synthesis failed for question '{}': {}", question, ex.getMessage());
            return null;
        }
    }
}
