package org.pahappa.systems.aiquery.metadata;

import org.pahappa.systems.aiquery.dto.FilterOperator;

import javax.persistence.metamodel.SingularAttribute;
import java.util.List;

/**
 * A single persistent attribute that has already passed the {@code @AiNotQueryable} check
 * and JPA-basic-type filter. Carries the actual {@link SingularAttribute} so downstream
 * criteria-building code never has to re-resolve a field by name.
 */
public final class ResolvedField {

    private final String name;
    private final SingularAttribute<?, ?> attribute;
    private final Class<?> javaType;
    private final boolean sortable;
    private final boolean filterable;
    private final List<FilterOperator> supportedOperators;
    private final List<String> allowedValues;

    public ResolvedField(String name, SingularAttribute<?, ?> attribute, Class<?> javaType, boolean sortable,
                          boolean filterable, List<FilterOperator> supportedOperators, List<String> allowedValues) {
        this.name = name;
        this.attribute = attribute;
        this.javaType = javaType;
        this.sortable = sortable;
        this.filterable = filterable;
        this.supportedOperators = supportedOperators;
        this.allowedValues = allowedValues;
    }

    public String getName() {
        return name;
    }

    public SingularAttribute<?, ?> getAttribute() {
        return attribute;
    }

    public Class<?> getJavaType() {
        return javaType;
    }

    public boolean isSortable() {
        return sortable;
    }

    public boolean isFilterable() {
        return filterable;
    }

    public List<FilterOperator> getSupportedOperators() {
        return supportedOperators;
    }

    public List<String> getAllowedValues() {
        return allowedValues;
    }
}
