package org.pahappa.systems.aiquery.dto;

import java.util.List;
import java.util.Objects;

/**
 * Optional aggregation intent. {@code field} is required for SUM/AVG/MIN/MAX and optional
 * for COUNT (omitted means COUNT(*)). {@code groupBy} is optional; when present, one result
 * row is returned per group. {@code subtractField} is optional and only valid with SUM/AVG: when
 * set, the aggregated numerator becomes {@code function(field) - function(subtractField)} instead
 * of just {@code function(field)} -- for entities that track a total and a remaining balance
 * rather than an amount already spent/used (e.g. {@code totalBudget}/{@code remainingBalance}).
 * {@code asPercentageOf} is optional and only meaningful alongside {@code groupBy}: when set, it
 * names one of the {@code groupBy} fields (a numeric field that is constant within each group,
 * e.g. a per-item budget ceiling) and the aggregated "value" of each group (after any
 * {@code subtractField} subtraction) is expressed as a percentage of that field's value instead of
 * the raw aggregate -- answering questions like "which item has used the biggest percentage of its
 * allocated budget".
 *
 * <p>{@code durationStartField}/{@code durationEndField} select a different aggregation mode
 * entirely: instead of aggregating a numeric {@code field}, the elapsed time between two
 * date/temporal fields on the entity (end minus start) is aggregated instead, in
 * {@code durationUnit} (defaults to {@link DurationUnit#DAYS} when null) -- e.g. "which stage
 * takes the longest on average" with {@code function=AVG}. Mutually exclusive with
 * {@code field}/{@code subtractField}; not supported with {@code function=COUNT}.
 */
public final class AggregationRequest {

    private final AggregationFunction function;
    private final String field;
    private final List<String> groupBy;
    private final String asPercentageOf;
    private final String subtractField;
    private final String durationStartField;
    private final String durationEndField;
    private final String durationUnit;

    public AggregationRequest(AggregationFunction function, String field, List<String> groupBy) {
        this(function, field, groupBy, null, null);
    }

    public AggregationRequest(AggregationFunction function, String field, List<String> groupBy, String asPercentageOf) {
        this(function, field, groupBy, asPercentageOf, null);
    }

    public AggregationRequest(AggregationFunction function, String field, List<String> groupBy, String asPercentageOf,
                               String subtractField) {
        this(function, field, groupBy, asPercentageOf, subtractField, null, null, null);
    }

    public AggregationRequest(AggregationFunction function, String field, List<String> groupBy, String asPercentageOf,
                               String subtractField, String durationStartField, String durationEndField,
                               String durationUnit) {
        this.function = function;
        this.field = field;
        this.groupBy = groupBy;
        this.asPercentageOf = asPercentageOf;
        this.subtractField = subtractField;
        this.durationStartField = durationStartField;
        this.durationEndField = durationEndField;
        this.durationUnit = durationUnit;
    }

    public AggregationFunction function() {
        return function;
    }

    public String field() {
        return field;
    }

    public List<String> groupBy() {
        return groupBy;
    }

    public String asPercentageOf() {
        return asPercentageOf;
    }

    public String subtractField() {
        return subtractField;
    }

    public String durationStartField() {
        return durationStartField;
    }

    public String durationEndField() {
        return durationEndField;
    }

    public String durationUnit() {
        return durationUnit;
    }

    // JavaBean-style accessors so JSON serializers that only auto-detect getXxx()/isXxx()
    // (e.g. the default Jackson message converter) can see these properties -- the record-style
    // methods above are for in-code callers.
    public AggregationFunction getFunction() {
        return function;
    }

    public String getField() {
        return field;
    }

    public List<String> getGroupBy() {
        return groupBy;
    }

    public String getAsPercentageOf() {
        return asPercentageOf;
    }

    public String getSubtractField() {
        return subtractField;
    }

    public String getDurationStartField() {
        return durationStartField;
    }

    public String getDurationEndField() {
        return durationEndField;
    }

    public String getDurationUnit() {
        return durationUnit;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AggregationRequest)) return false;
        AggregationRequest that = (AggregationRequest) o;
        return function == that.function && Objects.equals(field, that.field) && Objects.equals(groupBy, that.groupBy)
                && Objects.equals(asPercentageOf, that.asPercentageOf) && Objects.equals(subtractField, that.subtractField)
                && Objects.equals(durationStartField, that.durationStartField)
                && Objects.equals(durationEndField, that.durationEndField)
                && Objects.equals(durationUnit, that.durationUnit);
    }

    @Override
    public int hashCode() {
        return Objects.hash(function, field, groupBy, asPercentageOf, subtractField,
                durationStartField, durationEndField, durationUnit);
    }

    @Override
    public String toString() {
        return "AggregationRequest[function=" + function + ", field=" + field + ", groupBy=" + groupBy
                + ", asPercentageOf=" + asPercentageOf + ", subtractField=" + subtractField
                + ", durationStartField=" + durationStartField + ", durationEndField=" + durationEndField
                + ", durationUnit=" + durationUnit + "]";
    }
}
