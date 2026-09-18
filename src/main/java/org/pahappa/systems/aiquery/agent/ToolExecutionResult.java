package org.pahappa.systems.aiquery.agent;

import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;

/**
 * The outcome of one {@link QueryDbTool#execute(org.sers.webutils.model.security.User, String)}
 * call: either a successful, already-validated-and-executed query, or a failure whose {@link
 * #toolMessageContent()} is what gets sent back to the model as a {@code role: "tool"} message so
 * it can correct itself on a later attempt.
 */
public final class ToolExecutionResult {

    private final boolean success;
    private final TranslatedQueryRequest plan;
    private final QueryResult queryResult;
    private final String toolMessageContent;

    private ToolExecutionResult(boolean success, TranslatedQueryRequest plan, QueryResult queryResult,
                                 String toolMessageContent) {
        this.success = success;
        this.plan = plan;
        this.queryResult = queryResult;
        this.toolMessageContent = toolMessageContent;
    }

    public static ToolExecutionResult success(TranslatedQueryRequest plan, QueryResult queryResult,
                                               String toolMessageContent) {
        return new ToolExecutionResult(true, plan, queryResult, toolMessageContent);
    }

    /** {@code plan} may be null when the arguments themselves could not be parsed at all. */
    public static ToolExecutionResult failure(TranslatedQueryRequest plan, String toolMessageContent) {
        return new ToolExecutionResult(false, plan, null, toolMessageContent);
    }

    public boolean success() {
        return success;
    }

    /** The parsed plan, if parsing succeeded -- present even on a validation/authorization failure. */
    public TranslatedQueryRequest plan() {
        return plan;
    }

    /** Non-null only when {@link #success()}. */
    public QueryResult queryResult() {
        return queryResult;
    }

    /** What to send back as the {@code role: "tool"} message content, on success or failure alike. */
    public String toolMessageContent() {
        return toolMessageContent;
    }
}
