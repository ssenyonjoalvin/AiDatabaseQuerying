package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.metadata.ResolvedField;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;

import java.util.Objects;

/**
 * A field reference already resolved and authorization-checked against the query's root
 * entity: either one of its own fields directly, or -- one level deep only -- a field on a
 * single-valued ({@code @ManyToOne}/{@code @OneToOne}) related entity, reached via
 * {@link #relation()}. Produced exclusively by {@link AiQueryValidator}; {@link
 * org.pahappa.systems.aiquery.querybuilder.CriteriaQueryBuilder} never re-derives one from a
 * raw name.
 */
public final class ValidatedFieldPath {

    private final ResolvedRelation relation;
    private final ResolvedField field;

    public static ValidatedFieldPath direct(ResolvedField field) {
        return new ValidatedFieldPath(null, field);
    }

    public static ValidatedFieldPath viaRelation(ResolvedRelation relation, ResolvedField field) {
        return new ValidatedFieldPath(relation, field);
    }

    private ValidatedFieldPath(ResolvedRelation relation, ResolvedField field) {
        this.relation = relation;
        this.field = field;
    }

    /** True when this field belongs to the query's root entity itself, not a related one. */
    public boolean isDirect() {
        return relation == null;
    }

    /** Null when {@link #isDirect()}. */
    public ResolvedRelation relation() {
        return relation;
    }

    public ResolvedField field() {
        return field;
    }

    /** The name this field is addressed/output by: {@code "field"} or {@code "relation.field"}. */
    public String qualifiedName() {
        return isDirect() ? field.getName() : relation.getName() + "." + field.getName();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidatedFieldPath)) return false;
        ValidatedFieldPath that = (ValidatedFieldPath) o;
        return Objects.equals(relation, that.relation) && Objects.equals(field, that.field);
    }

    @Override
    public int hashCode() {
        return Objects.hash(relation, field);
    }

    @Override
    public String toString() {
        return "ValidatedFieldPath[" + qualifiedName() + "]";
    }
}
