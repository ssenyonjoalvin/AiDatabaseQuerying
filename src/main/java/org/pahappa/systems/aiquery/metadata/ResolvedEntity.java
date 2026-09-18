package org.pahappa.systems.aiquery.metadata;

import javax.persistence.metamodel.EntityType;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * A JPA-managed entity together with the subset of its persistent attributes that are
 * queryable by the AI (i.e. basic-typed, not {@code @AiNotQueryable}), plus its single-valued
 * relationships ({@link ResolvedRelation}) that let the AI reach one level into a related
 * entity's own basic fields. Built once from the JPA {@link javax.persistence.metamodel.Metamodel}
 * by {@link AiEntityMetadataService}.
 */
public final class ResolvedEntity {

    private final String entityName;
    private final EntityType<?> entityType;
    private final Class<?> javaType;
    private final Map<String, ResolvedField> fields;
    private final Map<String, ResolvedRelation> relations;

    public ResolvedEntity(String entityName, EntityType<?> entityType, Class<?> javaType,
                           Map<String, ResolvedField> fields, Map<String, ResolvedRelation> relations) {
        this.entityName = entityName;
        this.entityType = entityType;
        this.javaType = javaType;
        this.fields = fields;
        this.relations = relations;
    }

    public String getEntityName() {
        return entityName;
    }

    public EntityType<?> getEntityType() {
        return entityType;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public Map<String, ResolvedField> getFields() {
        return fields;
    }

    public Optional<ResolvedField> getField(String name) {
        return Optional.ofNullable(fields.get(name));
    }

    public Collection<ResolvedField> getAllFields() {
        return fields.values();
    }

    public Map<String, ResolvedRelation> getRelations() {
        return relations;
    }

    public Optional<ResolvedRelation> getRelation(String name) {
        return Optional.ofNullable(relations.get(name));
    }

    public Collection<ResolvedRelation> getAllRelations() {
        return relations.values();
    }
}
