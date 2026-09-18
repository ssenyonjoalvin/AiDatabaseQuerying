package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Objects;

/**
 * A set of {@link FilterCriterion} that are OR'd together, as one entry of a query's
 * {@code filterGroups}. Every top-level {@code filters} entry and every {@code FilterGroup}
 * is AND'd with everything else; within a group, its {@code anyOf} entries are OR'd --
 * e.g. one group with two criteria expresses "field A = X OR field B = Y".
 */
public final class FilterGroup {

    private final List<FilterCriterion> anyOf;

    public FilterGroup(List<FilterCriterion> anyOf) {
        this.anyOf = anyOf;
    }

    public List<FilterCriterion> anyOf() {
        return anyOf;
    }

    // JavaBean-style accessor so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see this property -- the record-style
    // method above is for in-code callers.
    public List<FilterCriterion> getAnyOf() {
        return anyOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FilterGroup)) return false;
        FilterGroup that = (FilterGroup) o;
        return Objects.equals(anyOf, that.anyOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anyOf);
    }

    @Override
    public String toString() {
        return "FilterGroup[anyOf=" + anyOf + "]";
    }
}
