package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Objects;

public final class QueryableEntityDescriptor {

    private final String entity;
    private final List<QueryableFieldDescriptor> fields;

    public QueryableEntityDescriptor(String entity, List<QueryableFieldDescriptor> fields) {
        this.entity = entity;
        this.fields = fields;
    }

    public String entity() {
        return entity;
    }

    public List<QueryableFieldDescriptor> fields() {
        return fields;
    }

    // JavaBean-style accessors so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see these properties -- the record-style
    // methods above are for in-code callers.
    public String getEntity() {
        return entity;
    }

    public List<QueryableFieldDescriptor> getFields() {
        return fields;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryableEntityDescriptor)) return false;
        QueryableEntityDescriptor that = (QueryableEntityDescriptor) o;
        return Objects.equals(entity, that.entity) && Objects.equals(fields, that.fields);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, fields);
    }

    @Override
    public String toString() {
        return "QueryableEntityDescriptor[entity=" + entity + ", fields=" + fields + "]";
    }
}
