package org.pahappa.systems.aiquery.dto;

import java.util.Objects;

public final class QueryableEntitySummary {

    private final String entity;

    public QueryableEntitySummary(String entity) {
        this.entity = entity;
    }

    public String entity() {
        return entity;
    }

    // JavaBean-style accessor so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see this property -- the record-style
    // method above is for in-code callers.
    public String getEntity() {
        return entity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryableEntitySummary)) return false;
        QueryableEntitySummary that = (QueryableEntitySummary) o;
        return Objects.equals(entity, that.entity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity);
    }

    @Override
    public String toString() {
        return "QueryableEntitySummary[entity=" + entity + "]";
    }
}
