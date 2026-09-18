package org.pahappa.systems.aiquery.dto;

import java.util.Objects;

public final class SortCriterion {

    private final String field;
    private final SortDirection direction;

    public SortCriterion(String field, SortDirection direction) {
        this.field = field;
        this.direction = direction;
    }

    public String field() {
        return field;
    }

    public SortDirection direction() {
        return direction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SortCriterion)) return false;
        SortCriterion that = (SortCriterion) o;
        return Objects.equals(field, that.field) && direction == that.direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(field, direction);
    }

    @Override
    public String toString() {
        return "SortCriterion[field=" + field + ", direction=" + direction + "]";
    }
}
