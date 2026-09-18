package org.pahappa.systems.aiquery.validation;

import java.util.List;
import java.util.Objects;

/** A validated, authorization-checked {@link org.pahappa.systems.aiquery.dto.FilterGroup}: its {@code anyOf} entries are OR'd together. */
public final class ValidatedFilterGroup {

    private final List<ValidatedFilter> anyOf;

    public ValidatedFilterGroup(List<ValidatedFilter> anyOf) {
        this.anyOf = anyOf;
    }

    public List<ValidatedFilter> anyOf() {
        return anyOf;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedFilterGroup)) return false;
        ValidatedFilterGroup that = (ValidatedFilterGroup) o;
        return Objects.equals(anyOf, that.anyOf);
    }

    @Override
    public int hashCode() {
        return Objects.hash(anyOf);
    }

    @Override
    public String toString() {
        return "ValidatedFilterGroup[anyOf=" + anyOf + "]";
    }
}
