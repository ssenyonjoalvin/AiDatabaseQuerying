package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Map;

/**
 * Response for {@code /ai-query/ask}: the raw {@link QueryResult} plus, when synthesis
 * succeeded, a natural-language answer grounded in those rows. {@code answer} is null when
 * synthesis wasn't attempted or failed -- callers fall back to displaying the raw table.
 */
public final class AskResponse {

    private final String answer;
    private final String entity;
    private final List<String> fields;
    private final List<Map<String, Object>> rows;
    private final int page;
    private final int pageSize;
    private final int returnedCount;
    private final boolean hasMore;
    private final AggregationRequest aggregation;

    public AskResponse(String answer, QueryResult result) {
        this.answer = answer;
        this.entity = result.entity();
        this.fields = result.fields();
        this.rows = result.rows();
        this.page = result.page();
        this.pageSize = result.pageSize();
        this.returnedCount = result.returnedCount();
        this.hasMore = result.hasMore();
        this.aggregation = result.aggregation();
    }

    public String answer() {
        return answer;
    }

    public String entity() {
        return entity;
    }

    public List<String> fields() {
        return fields;
    }

    public List<Map<String, Object>> rows() {
        return rows;
    }

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public int returnedCount() {
        return returnedCount;
    }

    public boolean hasMore() {
        return hasMore;
    }

    public AggregationRequest aggregation() {
        return aggregation;
    }

    // JavaBean-style accessors so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see these properties -- the record-style
    // methods above are for in-code callers.
    public String getAnswer() {
        return answer;
    }

    public String getEntity() {
        return entity;
    }

    public List<String> getFields() {
        return fields;
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public int getPage() {
        return page;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getReturnedCount() {
        return returnedCount;
    }

    public boolean isHasMore() {
        return hasMore;
    }

    public AggregationRequest getAggregation() {
        return aggregation;
    }
}
