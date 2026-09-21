package org.pahappa.systems.aiquery.config;

import org.springframework.stereotype.Component;

/**
 * Configurable limits enforced by the generic AI query system. These are hard
 * server-side ceilings the caller cannot override or negotiate around.
 *
 * <p>This module has no Spring Boot relaxed property binding, so values are plain field
 * defaults. To override them, declare this bean explicitly in the host application's
 * Spring config and set properties on it, e.g.:
 * <pre>{@code
 * <bean class="org.pahappa.aiquery.config.AiQueryProperties">
 *     <property name="defaultPageSize" value="25"/>
 *     <property name="maxPageSize" value="200"/>
 *     <property name="advisorySystemPrompt" value="..."/>
 * </bean>
 * }</pre>
 * If the host does not declare its own bean, component-scanning this package registers
 * one with the defaults below.
 */
@Component
public class AiQueryProperties {

    private int defaultPageSize = 20;
    private int maxPageSize = 100;
    private int maxFilters = 10;
    private int maxSelectedFields = 25;
    private int maxSortFields = 5;
    /** Cap on the number of OR-groups in a query's {@code filterGroups}. */
    private int maxFilterGroups = 5;
    /**
     * Safety cap on rows fetched for a duration aggregation (see {@code
     * CriteriaQueryBuilder#executeDurationAggregate}): the per-row duration and the
     * per-group average/sum/min/max are computed in Java rather than in SQL (there is no
     * portable, dialect-independent date-diff in JPA Criteria), so every candidate row has
     * to be pulled back before grouping/aggregating.
     */
    private int maxDurationRows = 5000;
    /** Reserved for future relationship-traversal support; joins are not implemented yet. */
    private int maxJoins = 0;
    /** Reserved for future relationship-traversal support; joins are not implemented yet. */
    private int maxJoinDepth = 0;
    /**
     * Once the caller's accessible entity count exceeds this, {@link
     * org.pahappa.systems.aiquery.service.NaturalLanguageQueryTranslator} first asks the model
     * a cheap, entity-names-only question to narrow down which entities the question is about,
     * before sending full field detail for just those entities. Below this threshold the whole
     * accessible schema is sent in one call as before.
     *
     * <p>Defaults effectively unbounded (full schema, every time, no narrowing call) -- entity
     * linking is a probabilistic LLM call and narrowing on it means a question can silently lose
     * access to a field it actually needed (e.g. a "which stage is slowest" question losing the
     * one entity with the relevant date fields) with no error, just a wrong or empty answer.
     * Hosts with a large accessible entity count and a small-context/rate-limited model (e.g.
     * free-tier Groq) can set this back down explicitly to trade completeness for prompt size.
     */
    private int schemaLinkingEntityThreshold = Integer.MAX_VALUE;
    /**
     * System prompt used by {@link org.pahappa.systems.aiquery.web.AiController} to answer
     * questions that don't map to any queryable entity/field, via {@code AiChatClient}'s free-text
     * fallback. Hosts should override this with wording specific to their own application.
     */
    /**
     * System prompt used by {@code QueryResultSynthesizer} to turn a question plus the exact
     * rows a database query returned for it into a natural-language answer. Hosts should
     * override this with wording specific to their own application and data.
     */
    private String resultSynthesisSystemPrompt =
            "You are a helpful assistant for this application. You already have the exact data " +
            "needed to answer the user's question -- it is provided below as JSON, returned by a " +
            "live database query. Write a concise, natural-language answer grounded only in that " +
            "data; never invent figures or names not present in it. If the JSON is empty, say " +
            "plainly that no matching records were found. If more rows exist beyond what's shown, " +
            "do not imply this is the complete dataset.";

    private String advisorySystemPrompt =
            "You are a helpful assistant for this application. The user asked a question that " +
            "isn't a database query -- answer it directly and helpfully in plain text.";

    /**
     * Master switch for the agentic (tool-calling) query loop. When {@code false}, {@code
     * AiController} falls back to the single-shot {@code NaturalLanguageQueryTranslator} path
     * exactly as before -- lets a host turn the loop off in production without a redeploy.
     */
    private boolean agentEnabled = true;

    /**
     * Hard ceiling on the number of {@code query_db} tool executions the agentic loop will run
     * for one question, enforced in Java rather than trusted to the model. On exhaustion, the
     * loop answers from its last successful result rather than looping further.
     */
    private int agentMaxIterations = 3;

    public int getDefaultPageSize() {
        return defaultPageSize;
    }

    public void setDefaultPageSize(int defaultPageSize) {
        this.defaultPageSize = defaultPageSize;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }

    public int getMaxFilters() {
        return maxFilters;
    }

    public void setMaxFilters(int maxFilters) {
        this.maxFilters = maxFilters;
    }

    public int getMaxSelectedFields() {
        return maxSelectedFields;
    }

    public void setMaxSelectedFields(int maxSelectedFields) {
        this.maxSelectedFields = maxSelectedFields;
    }

    public int getMaxSortFields() {
        return maxSortFields;
    }

    public void setMaxSortFields(int maxSortFields) {
        this.maxSortFields = maxSortFields;
    }

    public int getMaxFilterGroups() {
        return maxFilterGroups;
    }

    public void setMaxFilterGroups(int maxFilterGroups) {
        this.maxFilterGroups = maxFilterGroups;
    }

    public int getMaxDurationRows() {
        return maxDurationRows;
    }

    public void setMaxDurationRows(int maxDurationRows) {
        this.maxDurationRows = maxDurationRows;
    }

    public int getMaxJoins() {
        return maxJoins;
    }

    public void setMaxJoins(int maxJoins) {
        this.maxJoins = maxJoins;
    }

    public int getMaxJoinDepth() {
        return maxJoinDepth;
    }

    public void setMaxJoinDepth(int maxJoinDepth) {
        this.maxJoinDepth = maxJoinDepth;
    }

    public int getSchemaLinkingEntityThreshold() {
        return schemaLinkingEntityThreshold;
    }

    public void setSchemaLinkingEntityThreshold(int schemaLinkingEntityThreshold) {
        this.schemaLinkingEntityThreshold = schemaLinkingEntityThreshold;
    }

    public String getAdvisorySystemPrompt() {
        return advisorySystemPrompt;
    }

    public void setAdvisorySystemPrompt(String advisorySystemPrompt) {
        this.advisorySystemPrompt = advisorySystemPrompt;
    }

    public String getResultSynthesisSystemPrompt() {
        return resultSynthesisSystemPrompt;
    }

    public void setResultSynthesisSystemPrompt(String resultSynthesisSystemPrompt) {
        this.resultSynthesisSystemPrompt = resultSynthesisSystemPrompt;
    }

    public boolean isAgentEnabled() {
        return agentEnabled;
    }

    public void setAgentEnabled(boolean agentEnabled) {
        this.agentEnabled = agentEnabled;
    }

    public int getAgentMaxIterations() {
        return agentMaxIterations;
    }

    public void setAgentMaxIterations(int agentMaxIterations) {
        this.agentMaxIterations = agentMaxIterations;
    }
}
