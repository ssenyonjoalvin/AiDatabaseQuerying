package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class QueryResult {

    private final String entity;
    private final List<String> fields;
    private final List<Map<String, Object>> rows;
    private final int page;
    private final int pageSize;
    private final int returnedCount;
    private final boolean hasMore;
    private final AggregationRequest aggregation;

    public QueryResult(String entity, List<String> fields, List<Map<String, Object>> rows, int page, int pageSize,
                        int returnedCount, boolean hasMore, AggregationRequest aggregation) {
        this.entity = entity;
        this.fields = fields;
        this.rows = rows;
        this.page = page;
        this.pageSize = pageSize;
        this.returnedCount = returnedCount;
        this.hasMore = hasMore;
        this.aggregation = aggregation;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryResult)) return false;
        QueryResult that = (QueryResult) o;
        return page == that.page && pageSize == that.pageSize && returnedCount == that.returnedCount
                && hasMore == that.hasMore && Objects.equals(entity, that.entity) && Objects.equals(fields, that.fields)
                && Objects.equals(rows, that.rows) && Objects.equals(aggregation, that.aggregation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, fields, rows, page, pageSize, returnedCount, hasMore, aggregation);
    }

    @Override
    public String toString() {
        return "QueryResult[entity=" + entity + ", fields=" + fields + ", rows=" + rows + ", page=" + page
                + ", pageSize=" + pageSize + ", returnedCount=" + returnedCount + ", hasMore=" + hasMore
                + ", aggregation=" + aggregation + "]";
    }
}
