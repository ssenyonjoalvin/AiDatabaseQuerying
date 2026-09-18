package org.pahappa.systems.aiquery.dto;

import java.util.Collections;
import java.util.List;

/**
 * A fully-formed structured query request produced by translating a natural-language
 * question into the same shape {@code AiQueryService#queryEntity} accepts. Nothing here is
 * trusted -- it is re-validated by {@code AiQueryValidator} exactly like any other
 * caller-supplied query.
 */
public final class TranslatedQueryRequest {

    private final String entityName;
    private final List<String> fields;
    private final List<FilterCriterion> filters;
    private final List<FilterGroup> filterGroups;
    private final List<SortCriterion> sort;
    private final Integer page;
    private final Integer pageSize;
    private final AggregationRequest aggregation;

    public TranslatedQueryRequest(String entityName, List<String> fields, List<FilterCriterion> filters,
                                   List<SortCriterion> sort, Integer page, Integer pageSize,
                                   AggregationRequest aggregation) {
        this(entityName, fields, filters, Collections.<FilterGroup>emptyList(), sort, page, pageSize, aggregation);
    }

    public TranslatedQueryRequest(String entityName, List<String> fields, List<FilterCriterion> filters,
                                   List<FilterGroup> filterGroups, List<SortCriterion> sort, Integer page,
                                   Integer pageSize, AggregationRequest aggregation) {
        this.entityName = entityName;
        this.fields = fields;
        this.filters = filters;
        this.filterGroups = filterGroups;
        this.sort = sort;
        this.page = page;
        this.pageSize = pageSize;
        this.aggregation = aggregation;
    }

    public String entityName() {
        return entityName;
    }

    public List<String> fields() {
        return fields;
    }

    public List<FilterCriterion> filters() {
        return filters;
    }

    public List<FilterGroup> filterGroups() {
        return filterGroups;
    }

    public List<SortCriterion> sort() {
        return sort;
    }

    public Integer page() {
        return page;
    }

    public Integer pageSize() {
        return pageSize;
    }

    public AggregationRequest aggregation() {
        return aggregation;
    }
}
