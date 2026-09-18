package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.dto.SortDirection;

import java.util.Objects;

/**
 * {@code field} is null only when sorting by an aggregation's computed result (the reserved
 * "value" alias) rather than by one of the entity's own fields.
 */
public final class ValidatedSort {

    private final ValidatedFieldPath field;
    private final SortDirection direction;

    public ValidatedSort(ValidatedFieldPath field, SortDirection direction) {
        this.field = field;
        this.direction = direction;
    }

    public ValidatedFieldPath field() {
        return field;
    }

    public SortDirection direction() {
        return direction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedSort)) return false;
        ValidatedSort that = (ValidatedSort) o;
        return Objects.equals(field, that.field) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }

    @Override
    public String toString() {
        return "ValidatedSort[field=" + field + ", direction=" + direction + "]";
    }
}
