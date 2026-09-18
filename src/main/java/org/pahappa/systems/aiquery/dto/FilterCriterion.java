package org.pahappa.systems.aiquery.dto;

import java.util.Objects;

/**
 * A single structured filter condition supplied by the model. {@code value} is a plain
 * scalar (string/number/boolean) for most operators, a list for {@link FilterOperator#IN},
 * and is ignored for {@link FilterOperator#IS_NULL}/{@link FilterOperator#IS_NOT_NULL}.
 * Never a SQL/JPQL fragment or expression -- it is only ever used as a bind value.
 */
public final class FilterCriterion {

    private final String field;
    private final FilterOperator operator;
    private final Object value;

    public FilterCriterion(String field, FilterOperator operator, Object value) {
        this.field = field;
        this.operator = operator;
        this.value = value;
    }

    public String field() {
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
        if (!(o instanceof FilterCriterion)) return false;
        FilterCriterion that = (FilterCriterion) o;
        return Objects.equals(field, that.field) && operator == that.operator && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, operator, value);
    }

    @Override
    public String toString() {
        return "FilterCriterion[field=" + field + ", operator=" + operator + ", value=" + value + "]";
    }
}
