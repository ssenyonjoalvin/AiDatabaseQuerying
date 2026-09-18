package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.metadata.ResolvedEntity;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The fully validated, authorization-checked, limit-checked result of
 * {@link AiQueryValidator}. Everything downstream ({@code CriteriaQueryBuilder}) consumes
 * only this object -- it never sees the caller's raw strings again.
 */
public final class ValidatedQuery {

    private final ResolvedEntity entity;
    private final List<ValidatedFieldPath> selectedFields;
    private final List<ValidatedFilter> filters;
    private final List<ValidatedFilterGroup> filterGroups;
    private final List<ValidatedSort> sorts;
    private final int page;
    private final int pageSize;
    private final ValidatedAggregation aggregation;

    public ValidatedQuery(ResolvedEntity entity, List<ValidatedFieldPath> selectedFields, List<ValidatedFilter> filters,
                           List<ValidatedSort> sorts, int page, int pageSize, ValidatedAggregation aggregation) {
        this(entity, selectedFields, filters, Collections.<ValidatedFilterGroup>emptyList(), sorts, page, pageSize, aggregation);
    }

    public ValidatedQuery(ResolvedEntity entity, List<ValidatedFieldPath> selectedFields, List<ValidatedFilter> filters,
                           List<ValidatedFilterGroup> filterGroups, List<ValidatedSort> sorts, int page, int pageSize,
                           ValidatedAggregation aggregation) {
        this.entity = entity;
        this.selectedFields = selectedFields;
        this.filters = filters;
        this.filterGroups = filterGroups;
        this.sorts = sorts;
        this.page = page;
        this.pageSize = pageSize;
        this.aggregation = aggregation;
    }

    public ResolvedEntity entity() {
        return entity;
    }

    public List<ValidatedFieldPath> selectedFields() {
        return selectedFields;
    }

    public List<ValidatedFilter> filters() {
        return filters;
    }

    public List<ValidatedFilterGroup> filterGroups() {
        return filterGroups;
    }

    public List<ValidatedSort> sorts() {
        return sorts;
    }

    public int page() {
        return page;
    }

    public int pageSize() {
        return pageSize;
    }

    public ValidatedAggregation aggregation() {
        return aggregation;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedQuery)) return false;
        ValidatedQuery that = (ValidatedQuery) o;
        return page == that.page && pageSize == that.pageSize && Objects.equals(entity, that.entity)
                && Objects.equals(selectedFields, that.selectedFields) && Objects.equals(filters, that.filters)
                && Objects.equals(filterGroups, that.filterGroups)
                && Objects.equals(sorts, that.sorts) && Objects.equals(aggregation, that.aggregation);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entity, selectedFields, filters, filterGroups, sorts, page, pageSize, aggregation);
    }

    @Override
    public String toString() {
        return "ValidatedQuery[entity=" + entity + ", selectedFields=" + selectedFields + ", filters=" + filters
                + ", filterGroups=" + filterGroups + ", sorts=" + sorts + ", page=" + page + ", pageSize=" + pageSize
                + ", aggregation=" + aggregation + "]";
    }
}
