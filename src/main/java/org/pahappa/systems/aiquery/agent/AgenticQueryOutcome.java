package org.pahappa.systems.aiquery.agent;

import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;

/**
 * The result of one successful {@link AgenticQueryOrchestrator#run} call: the plan that produced
 * the final data, the data itself, and whether the loop had to stop because it hit its iteration
 * cap rather than because the model was actually satisfied with the answer.
 */
public final class AgenticQueryOutcome {

    private final TranslatedQueryRequest plan;
    private final QueryResult result;
    private final boolean lowConfidence;

    public AgenticQueryOutcome(TranslatedQueryRequest plan, QueryResult result, boolean lowConfidence) {
        this.plan = plan;
        this.result = result;
        this.lowConfidence = lowConfidence;
    }

    public TranslatedQueryRequest plan() {
        return plan;
    }

    public QueryResult result() {
        return result;
    }

    /** True only when the iteration cap was hit before the model itself stopped calling the tool. */
    public boolean lowConfidence() {
        return lowConfidence;
    }
}
