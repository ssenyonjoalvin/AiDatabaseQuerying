package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.dto.FilterOperator;

import java.util.Objects;

public final class ValidatedFilter {

    private final ValidatedFieldPath field;
    private final FilterOperator operator;
    private final Object value;

    public ValidatedFilter(ValidatedFieldPath field, FilterOperator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public ValidatedFieldPath field() {
        return field;
    }

    public FilterOperator operator() {
        return operator;
    }

    public Object value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedFilter)) return false;
        ValidatedFilter that = (ValidatedFilter) o;
        return Objects.equals(field, that.field) && operator == that.operator && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value);
    }

    @Override
    public String toString() {
        return "ValidatedFilter[field=" + field + ", operator=" + operator + ", value=" + value + "]";
    }
}
