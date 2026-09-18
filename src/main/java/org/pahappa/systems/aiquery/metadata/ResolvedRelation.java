package org.pahappa.systems.aiquery.metadata;

import javax.persistence.metamodel.SingularAttribute;

/**
 * A single-valued ({@code @ManyToOne}/{@code @OneToOne}) association, exposed to the AI as a
 * set of dotted {@code "relationName.fieldName"} references to the related entity's own basic
 * fields -- one level deep only. Built once from the JPA {@link javax.persistence.metamodel.Metamodel}
 * by {@link AiEntityMetadataService}, alongside {@link ResolvedField}. Collection associations
 * and fields on the related entity that are themselves relationships are never exposed.
 */
public final class ResolvedRelation {

    private final String name;
    private final SingularAttribute<?, ?> attribute;
    private final ResolvedEntity targetEntity;

    public ResolvedRelation(String name, SingularAttribute<?, ?> attribute, ResolvedEntity targetEntity) {
        this.name = name;
        this.attribute = attribute;
        this.targetEntity = targetEntity;
    }

    public String getName() {
        return name;
    }

    public SingularAttribute<?, ?> getAttribute() {
        return attribute;
    }

    public ResolvedEntity getTargetEntity() {
        return targetEntity;
    }
}
