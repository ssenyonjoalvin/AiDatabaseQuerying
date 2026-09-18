package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Objects;

public final class QueryableFieldDescriptor {

    private final String name;
    private final String type;
    private final boolean filterable;
    private final boolean sortable;
    private final List<FilterOperator> supportedOperators;
    private final List<String> allowedValues;

    public QueryableFieldDescriptor(String name, String type, boolean filterable, boolean sortable,
                                     List<FilterOperator> supportedOperators, List<String> allowedValues) {
        this.name = name;
        this.type = type;
        this.filterable = filterable;
        this.sortable = sortable;
        this.supportedOperators = supportedOperators;
        this.allowedValues = allowedValues;
    }

    public String name() {
        return name;
    }

    public String type() {
        return type;
    }

    public boolean filterable() {
        return filterable;
    }

    public boolean sortable() {
        return sortable;
    }

    public List<FilterOperator> supportedOperators() {
        return supportedOperators;
    }

    public List<String> allowedValues() {
        return allowedValues;
    }

    // JavaBean-style accessors so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see these properties -- the record-style
    // methods above are for in-code callers.
    public String getName() {
        return name;
    }

    public String getType() {
        return type;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public boolean isSortable() {
        return sortable;
    }

    public List<FilterOperator> getSupportedOperators() {
        return supportedOperators;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof QueryableFieldDescriptor)) return false;
        QueryableFieldDescriptor that = (QueryableFieldDescriptor) o;
        return filterable == that.filterable && sortable == that.sortable
                && Objects.equals(name, that.name) && Objects.equals(type, that.type)
                && Objects.equals(supportedOperators, that.supportedOperators)
                && Objects.equals(allowedValues, that.allowedValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, filterable, sortable, supportedOperators, allowedValues);
    }

    @Override
    public String toString() {
        return "QueryableFieldDescriptor[name=" + name + ", type=" + type + ", filterable=" + filterable
                + ", sortable=" + sortable + ", supportedOperators=" + supportedOperators
                + ", allowedValues=" + allowedValues + "]";
    }
}
