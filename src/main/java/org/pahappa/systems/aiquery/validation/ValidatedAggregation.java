package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.DurationUnit;

import java.util.List;
import java.util.Objects;

/**
 * {@code field} is null only for {@code COUNT(*)} or when this is a duration aggregation
 * (see {@link #durationStart()}). {@code percentageOfField}, when present, is always one of
 * the entries already present in {@code groupBy} -- see {@link
 * org.pahappa.systems.aiquery.dto.AggregationRequest#asPercentageOf()}. {@code subtractField},
 * when present, is always numeric and paired with a SUM/AVG {@code function} -- see {@link
 * org.pahappa.systems.aiquery.dto.AggregationRequest#subtractField()}. {@code durationStart}/
 * {@code durationEnd}, when present, are both temporal fields and {@code field}/
 * {@code subtractField} are both null -- see {@link
 * org.pahappa.systems.aiquery.dto.AggregationRequest#durationStartField()}.
 */
public final class ValidatedAggregation {

    private final AggregationFunction function;
    private final ValidatedFieldPath field;
    private final List<ValidatedFieldPath> groupBy;
    private final ValidatedFieldPath percentageOfField;
    private final ValidatedFieldPath subtractField;
    private final ValidatedFieldPath durationStart;
    private final ValidatedFieldPath durationEnd;
    private final DurationUnit durationUnit;

    public ValidatedAggregation(AggregationFunction function, ValidatedFieldPath field, List<ValidatedFieldPath> groupBy) {
        this(function, field, groupBy, null, null);
    }

    public ValidatedAggregation(AggregationFunction function, ValidatedFieldPath field, List<ValidatedFieldPath> groupBy,
                                 ValidatedFieldPath percentageOfField) {
        this(function, field, groupBy, percentageOfField, null);
    }

    public ValidatedAggregation(AggregationFunction function, ValidatedFieldPath field, List<ValidatedFieldPath> groupBy,
                                 ValidatedFieldPath percentageOfField, ValidatedFieldPath subtractField) {
        this(function, field, groupBy, percentageOfField, subtractField, null, null, null);
    }

    public ValidatedAggregation(AggregationFunction function, ValidatedFieldPath field, List<ValidatedFieldPath> groupBy,
                                 ValidatedFieldPath percentageOfField, ValidatedFieldPath subtractField,
                                 ValidatedFieldPath durationStart, ValidatedFieldPath durationEnd, DurationUnit durationUnit) {
        this.function = function;
        this.field = field;
        this.groupBy = groupBy;
        this.percentageOfField = percentageOfField;
        this.subtractField = subtractField;
        this.durationStart = durationStart;
        this.durationEnd = durationEnd;
        this.durationUnit = durationUnit;
    }

    public AggregationFunction function() {
        return function;
    }

    public ValidatedFieldPath field() {
        return field;
    }

    public List<ValidatedFieldPath> groupBy() {
        return groupBy;
    }

    public ValidatedFieldPath percentageOfField() {
        return percentageOfField;
    }

    public ValidatedFieldPath subtractField() {
        return subtractField;
    }

    public ValidatedFieldPath durationStart() {
        return durationStart;
    }

    public ValidatedFieldPath durationEnd() {
        return durationEnd;
    }

    public DurationUnit durationUnit() {
        return durationUnit;
    }

    public boolean isDuration() {
        return durationStart != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedAggregation)) return false;
        ValidatedAggregation that = (ValidatedAggregation) o;
        return function == that.function && Objects.equals(field, that.field) && Objects.equals(groupBy, that.groupBy)
                && Objects.equals(percentageOfField, that.percentageOfField) && Objects.equals(subtractField, that.subtractField)
                && Objects.equals(durationStart, that.durationStart) && Objects.equals(durationEnd, that.durationEnd)
                && durationUnit == that.durationUnit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(function, field, groupBy, percentageOfField, subtractField, durationStart, durationEnd, durationUnit);
    }

    @Override
    public String toString() {
        return "ValidatedAggregation[function=" + function + ", field=" + field + ", groupBy=" + groupBy
                + ", percentageOfField=" + percentageOfField + ", subtractField=" + subtractField
                + ", durationStart=" + durationStart + ", durationEnd=" + durationEnd + ", durationUnit=" + durationUnit + "]";
    }
}
