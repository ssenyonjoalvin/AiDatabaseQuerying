package org.pahappa.systems.aiquery.validation;

import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.DurationUnit;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.pahappa.systems.aiquery.exception.AiQueryAuthorizationException;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.metadata.AiEntityMetadataService;
import org.pahappa.systems.aiquery.metadata.ResolvedEntity;
import org.pahappa.systems.aiquery.metadata.ResolvedField;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;
import org.pahappa.systems.aiquery.security.AiDatabaseAuthorizationService;
import org.pahappa.systems.aiquery.typeconversion.AiTypeConversionService;
import org.sers.webutils.model.security.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Independently re-validates every part of a query request before it can reach the
 * database: the entity exists and is authorized, every field exists, is not
 * {@code @AiNotQueryable}, and is authorized, every operator is type-compatible, every
 * value converts safely, and every limit (filters, fields, sort, page size) is respected.
 * Nothing here trusts the caller -- a request is only ever resolved through
 * {@link AiEntityMetadataService}, never through raw reflection or string concatenation.
 */
@Service
public class AiQueryValidator {

    private static final String RESERVED_AGGREGATION_ALIAS = "value";

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;
    private final AiTypeConversionService typeConversionService;
    private final AiQueryProperties properties;

    @Autowired
    public AiQueryValidator(AiEntityMetadataService metadataService,
                             AiDatabaseAuthorizationService authorizationService,
                             AiTypeConversionService typeConversionService,
                             AiQueryProperties properties) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
        this.typeConversionService = typeConversionService;
        this.properties = properties;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public ResolvedEntity validateEntityAccess(User user, String entityName) {
        if (isBlank(entityName)) {
            throw new AiQueryValidationException("Entity name is required.");
        }
        ResolvedEntity entity = metadataService.findEntity(entityName)
                .orElseThrow(new EntityNotFoundSupplier(entityName));
        if (!authorizationService.canAccessEntity(user, entity.getEntityName())) {
            throw new AiQueryAuthorizationException("Access to entity '" + entity.getEntityName() + "' is not permitted.");
        }
        return entity;
    }

    /**
     * Resolves and authorization-checks a raw field name against {@code entity}: either a plain
     * field of its own, or -- exactly one level deep -- a {@code "relation.field"} reference
     * into one of its single-valued ({@code @ManyToOne}/{@code @OneToOne}) relationships. Access
     * to the relationship itself is gated the same way as any other field, via {@link
     * AiDatabaseAuthorizationService#canAccessField(User, String, String)} keyed by the
     * relationship's attribute name -- a host can block a join by denying that name even if the
     * target field would otherwise be accessible directly on its own entity.
     */
    private ValidatedFieldPath resolveFieldPath(User user, ResolvedEntity entity, String rawFieldName) {
        if (isBlank(rawFieldName)) {
            throw new AiQueryValidationException("Field name is required.");
        }
        int firstDot = rawFieldName.indexOf('.');
        if (firstDot < 0) {
            return ValidatedFieldPath.direct(validateOwnFieldAccess(user, entity, rawFieldName));
        }
        if (rawFieldName.indexOf('.', firstDot + 1) >= 0) {
            throw new AiQueryValidationException(
                    "Only one level of relationship traversal ('relation.field') is supported: '" + rawFieldName + "'.");
        }
        String relationName = rawFieldName.substring(0, firstDot);
        String targetFieldName = rawFieldName.substring(firstDot + 1);
        if (isBlank(relationName) || isBlank(targetFieldName)) {
            throw new AiQueryValidationException("Invalid relationship field path: '" + rawFieldName + "'.");
        }
        ResolvedRelation relation = entity.getRelation(relationName)
                .orElseThrow(new RelationNotFoundSupplier(relationName, entity.getEntityName()));
        if (!authorizationService.canAccessField(user, entity.getEntityName(), relationName)) {
            throw new AiQueryAuthorizationException("Access to relationship '" + relationName + "' on entity '"
                    + entity.getEntityName() + "' is not permitted.");
        }
        ResolvedEntity targetEntity = relation.getTargetEntity();
        // Named by the relation, never by targetEntity.getEntityName(): the schema shown to the
        // caller only ever lists "relation.field", never the target entity's own catalog name, so
        // echoing that name back here on a bad field guess would disclose it even when the target
        // entity isn't independently accessible (or listed) to this caller at all.
        ResolvedField targetField = targetEntity.getField(targetFieldName)
                .orElseThrow(new RelationFieldNotFoundSupplier(targetFieldName, relationName));
        if (!authorizationService.canAccessField(user, targetEntity.getEntityName(), targetField.getName())) {
            throw new AiQueryAuthorizationException("Access to field '" + targetField.getName() + "' on entity '"
                    + targetEntity.getEntityName() + "' is not permitted.");
        }
        return ValidatedFieldPath.viaRelation(relation, targetField);
    }

    private ResolvedField validateOwnFieldAccess(User user, ResolvedEntity entity, String fieldName) {
        ResolvedField field = entity.getField(fieldName)
                .orElseThrow(new FieldNotFoundSupplier(fieldName, entity.getEntityName()));
        if (!authorizationService.canAccessField(user, entity.getEntityName(), field.getName())) {
            throw new AiQueryAuthorizationException(
                    "Access to field '" + field.getName() + "' on entity '" + entity.getEntityName() + "' is not permitted.");
        }
        return field;
    }

    public ValidatedQuery validateQuery(User user, String entityName, List<String> fields,
                                        List<FilterCriterion> filters, List<SortCriterion> sorts,
                                        Integer page, Integer pageSize, AggregationRequest aggregation) {
        return validateQuery(user, entityName, fields, filters, Collections.<FilterGroup>emptyList(), sorts,
                page, pageSize, aggregation);
    }

    public ValidatedQuery validateQuery(User user, String entityName, List<String> fields,
                                        List<FilterCriterion> filters, List<FilterGroup> filterGroups,
                                        List<SortCriterion> sorts,
                                        Integer page, Integer pageSize, AggregationRequest aggregation) {
        ResolvedEntity entity = validateEntityAccess(user, entityName);

        ValidatedAggregation validatedAggregation = null;
        List<ValidatedFieldPath> selectedFields;

        if (aggregation != null) {
            validatedAggregation = validateAggregation(user, entity, aggregation);
            selectedFields = Collections.emptyList();
        } else {
            selectedFields = validateSelectedFields(user, entity, fields);
        }

        List<ValidatedFilter> validatedFilters = validateFilters(user, entity, filters);
        List<ValidatedFilterGroup> validatedFilterGroups = validateFilterGroups(user, entity, filterGroups);
        List<ValidatedSort> validatedSorts = validateSorts(user, entity, sorts, validatedAggregation);
        if (validatedAggregation == null) {
            selectedFields = includeSortFields(selectedFields, validatedSorts);
        }

        int effectivePage = page == null ? 0 : page;
        if (effectivePage < 0) {
            throw new AiQueryValidationException("Page must not be negative.");
        }
        int effectivePageSize = pageSize == null ? properties.getDefaultPageSize() : pageSize;
        if (effectivePageSize <= 0) {
            throw new AiQueryValidationException("Page size must be positive.");
        }
        if (effectivePageSize > properties.getMaxPageSize()) {
            throw new AiQueryValidationException("Page size exceeds the maximum of " + properties.getMaxPageSize() + ".");
        }

        return new ValidatedQuery(entity, selectedFields, validatedFilters, validatedFilterGroups, validatedSorts,
                effectivePage, effectivePageSize, validatedAggregation);
    }

    private List<ValidatedFieldPath> validateSelectedFields(User user, ResolvedEntity entity, List<String> fields) {
        boolean explicit = fields != null && !fields.isEmpty();
        List<String> requested = explicit ? fields : new ArrayList<String>(entity.getFields().keySet());

        if (explicit && requested.size() > properties.getMaxSelectedFields()) {
            throw new AiQueryValidationException("Too many fields requested (max " + properties.getMaxSelectedFields() + ").");
        }

        List<ValidatedFieldPath> resolved = new ArrayList<ValidatedFieldPath>();
        for (String fieldName : requested) {
            if (explicit) {
                resolved.add(resolveFieldPath(user, entity, fieldName));
            } else {
                try {
                    resolved.add(resolveFieldPath(user, entity, fieldName));
                } catch (AiQueryAuthorizationException ex) {
                    // Default field set: silently drop fields this user isn't authorized for
                    // instead of failing the whole query. An explicit request still fails loudly.
                }
            }
        }
        if (resolved.isEmpty()) {
            throw new AiQueryValidationException("No queryable fields are available for this request.");
        }
        return resolved;
    }

    /**
     * A plain (non-aggregated) query's output must always include whatever field it's sorted
     * on -- otherwise an answer to "which X has the highest/largest Y" comes back with no way to
     * see the Y value that actually determined the ranking, even though the ranking itself was
     * correct. (The aggregation path needs no equivalent: its output already always includes
     * every {@code groupBy} field plus "value" regardless of what was requested.)
     */
    private List<ValidatedFieldPath> includeSortFields(List<ValidatedFieldPath> selectedFields, List<ValidatedSort> sorts) {
        if (sorts.isEmpty()) {
            return selectedFields;
        }
        List<ValidatedFieldPath> result = new ArrayList<ValidatedFieldPath>(selectedFields);
        for (ValidatedSort sort : sorts) {
            if (sort.field() != null && !result.contains(sort.field())) {
                result.add(sort.field());
            }
        }
        return result;
    }

    private List<ValidatedFilter> validateFilters(User user, ResolvedEntity entity, List<FilterCriterion> filters) {
        if (filters == null || filters.isEmpty()) {
            return Collections.emptyList();
        }
        if (filters.size() > properties.getMaxFilters()) {
            throw new AiQueryValidationException("Too many filters requested (max " + properties.getMaxFilters() + ").");
        }
        List<ValidatedFilter> validated = new ArrayList<ValidatedFilter>();
        for (FilterCriterion criterion : filters) {
            if (criterion.operator() == null) {
                throw new AiQueryValidationException("Filter operator is required for field '" + criterion.field() + "'.");
            }
            ValidatedFieldPath fieldPath = resolveFieldPath(user, entity, criterion.field());
            ResolvedField field = fieldPath.field();
            if (!typeConversionService.isOperatorSupported(field.getJavaType(), criterion.operator())) {
                throw new AiQueryValidationException(
                        "Operator " + criterion.operator() + " is not supported for field '" + fieldPath.qualifiedName() + "'.");
            }
            Object converted = typeConversionService.convertForOperator(
                    field.getName(), field.getJavaType(), criterion.operator(), criterion.value());
            validated.add(new ValidatedFilter(fieldPath, criterion.operator(), converted));
        }
        return validated;
    }

    /**
     * Validates {@code filterGroups}: each group's {@code anyOf} entries are resolved and
     * type-checked exactly like a plain {@code filters} entry (see {@link #validateFilters}),
     * then OR'd together by {@code CriteriaQueryBuilder}; the resulting per-group predicate is
     * AND'd with every other filter/group, so "field A = X OR field B = Y" becomes expressible
     * as a single group.
     */
    private List<ValidatedFilterGroup> validateFilterGroups(User user, ResolvedEntity entity, List<FilterGroup> filterGroups) {
        if (filterGroups == null || filterGroups.isEmpty()) {
            return Collections.emptyList();
        }
        if (filterGroups.size() > properties.getMaxFilterGroups()) {
            throw new AiQueryValidationException("Too many filter groups requested (max " + properties.getMaxFilterGroups() + ").");
        }
        List<ValidatedFilterGroup> validated = new ArrayList<ValidatedFilterGroup>();
        for (FilterGroup group : filterGroups) {
            List<FilterCriterion> anyOf = group == null ? null : group.anyOf();
            if (anyOf == null || anyOf.isEmpty()) {
                throw new AiQueryValidationException("A filter group must contain at least one condition.");
            }
            if (anyOf.size() > properties.getMaxFilters()) {
                throw new AiQueryValidationException("Too many conditions in a filter group (max " + properties.getMaxFilters() + ").");
            }
            validated.add(new ValidatedFilterGroup(validateFilters(user, entity, anyOf)));
        }
        return validated;
    }

    private List<ValidatedSort> validateSorts(User user, ResolvedEntity entity, List<SortCriterion> sorts,
                                               ValidatedAggregation aggregation) {
        if (sorts == null || sorts.isEmpty()) {
            return Collections.emptyList();
        }
        if (sorts.size() > properties.getMaxSortFields()) {
            throw new AiQueryValidationException("Too many sort fields requested (max " + properties.getMaxSortFields() + ").");
        }
        List<ValidatedSort> validated = new ArrayList<ValidatedSort>();
        for (SortCriterion criterion : sorts) {
            if (criterion.direction() == null) {
                throw new AiQueryValidationException("Sort direction is required for field '" + criterion.field() + "'.");
            }
            if (aggregation != null && RESERVED_AGGREGATION_ALIAS.equalsIgnoreCase(criterion.field())) {
                validated.add(new ValidatedSort(null, criterion.direction()));
                continue;
            }
            ValidatedFieldPath fieldPath = resolveFieldPath(user, entity, criterion.field());
            if (!fieldPath.field().isSortable()) {
                throw new AiQueryValidationException("Field '" + fieldPath.qualifiedName() + "' is not sortable.");
            }
            if (aggregation != null && !aggregation.groupBy().contains(fieldPath)) {
                throw new AiQueryValidationException("When aggregating, sort fields must be either '"
                        + RESERVED_AGGREGATION_ALIAS + "' or one of the group-by fields; '" + fieldPath.qualifiedName() + "' is neither.");
            }
            validated.add(new ValidatedSort(fieldPath, criterion.direction()));
        }
        return validated;
    }

    private ValidatedAggregation validateAggregation(User user, ResolvedEntity entity, AggregationRequest aggregation) {
        if (aggregation.function() == null) {
            throw new AiQueryValidationException("Aggregation function is required.");
        }

        boolean isDuration = !isBlank(aggregation.durationStartField()) || !isBlank(aggregation.durationEndField());

        ValidatedFieldPath aggregatedField = null;
        ValidatedFieldPath durationStart = null;
        ValidatedFieldPath durationEnd = null;
        DurationUnit durationUnit = null;

        if (isDuration) {
            if (!isBlank(aggregation.field()) || !isBlank(aggregation.subtractField()) || !isBlank(aggregation.asPercentageOf())) {
                throw new AiQueryValidationException(
                        "'durationStartField'/'durationEndField' cannot be combined with 'field', 'subtractField', or 'asPercentageOf'.");
            }
            if (aggregation.function() == AggregationFunction.COUNT) {
                throw new AiQueryValidationException("COUNT is not supported for a duration aggregation.");
            }
            if (isBlank(aggregation.durationStartField()) || isBlank(aggregation.durationEndField())) {
                throw new AiQueryValidationException("Both 'durationStartField' and 'durationEndField' are required for a duration aggregation.");
            }
            durationStart = resolveFieldPath(user, entity, aggregation.durationStartField());
            durationEnd = resolveFieldPath(user, entity, aggregation.durationEndField());
            if (!typeConversionService.isTemporal(durationStart.field().getJavaType())) {
                throw new AiQueryValidationException("'durationStartField' field '" + durationStart.qualifiedName() + "' must be a date/time field.");
            }
            if (!typeConversionService.isTemporal(durationEnd.field().getJavaType())) {
                throw new AiQueryValidationException("'durationEndField' field '" + durationEnd.qualifiedName() + "' must be a date/time field.");
            }
            durationUnit = parseDurationUnit(aggregation.durationUnit());
        } else if (aggregation.function() == AggregationFunction.COUNT) {
            if (!isBlank(aggregation.field())) {
                aggregatedField = resolveFieldPath(user, entity, aggregation.field());
            }
        } else {
            if (isBlank(aggregation.field())) {
                throw new AiQueryValidationException("Aggregation field is required for " + aggregation.function() + ".");
            }
            aggregatedField = resolveFieldPath(user, entity, aggregation.field());
            boolean requiresNumeric = aggregation.function() == AggregationFunction.SUM
                    || aggregation.function() == AggregationFunction.AVG;
            if (requiresNumeric && !typeConversionService.isNumeric(aggregatedField.field().getJavaType())) {
                throw new AiQueryValidationException("Aggregation " + aggregation.function() + " requires a numeric field.");
            }
        }

        List<String> groupByNames = aggregation.groupBy() == null ? Collections.<String>emptyList() : aggregation.groupBy();
        if (groupByNames.size() > properties.getMaxSelectedFields()) {
            throw new AiQueryValidationException("Too many group-by fields requested (max " + properties.getMaxSelectedFields() + ").");
        }
        List<ValidatedFieldPath> groupByFields = new ArrayList<ValidatedFieldPath>();
        for (String groupByName : groupByNames) {
            if (RESERVED_AGGREGATION_ALIAS.equalsIgnoreCase(groupByName)) {
                throw new AiQueryValidationException("'" + RESERVED_AGGREGATION_ALIAS + "' is a reserved name and cannot be used as a group-by field.");
            }
            groupByFields.add(resolveFieldPath(user, entity, groupByName));
        }

        ValidatedFieldPath percentageOfField = isDuration ? null : validatePercentageOf(user, entity, aggregation, groupByFields);
        ValidatedFieldPath subtractField = isDuration ? null : validateSubtractField(user, entity, aggregation);

        return new ValidatedAggregation(aggregation.function(), aggregatedField, groupByFields, percentageOfField,
                subtractField, durationStart, durationEnd, durationUnit);
    }

    private DurationUnit parseDurationUnit(String raw) {
        if (isBlank(raw)) {
            return DurationUnit.DAYS;
        }
        try {
            return DurationUnit.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new AiQueryValidationException("Invalid 'durationUnit': '" + raw + "'.");
        }
    }

    /**
     * {@code subtractField} lets the numerator be {@code function(field) - function(subtractField)}
     * instead of just {@code function(field)} -- for entities that expose a total and a remaining
     * balance rather than an amount already used (e.g. {@code totalBudget}/{@code remainingBalance}),
     * there is no "amount used" column to aggregate directly.
     */
    private ValidatedFieldPath validateSubtractField(User user, ResolvedEntity entity, AggregationRequest aggregation) {
        if (isBlank(aggregation.subtractField())) {
            return null;
        }
        if (aggregation.function() != AggregationFunction.SUM && aggregation.function() != AggregationFunction.AVG) {
            throw new AiQueryValidationException("'subtractField' is only supported with SUM or AVG.");
        }
        ValidatedFieldPath resolved = resolveFieldPath(user, entity, aggregation.subtractField());
        if (!typeConversionService.isNumeric(resolved.field().getJavaType())) {
            throw new AiQueryValidationException("'subtractField' field '" + resolved.qualifiedName() + "' must be numeric.");
        }
        return resolved;
    }

    /**
     * {@code asPercentageOf} must name a numeric field already present in {@code groupBy} --
     * that's what lets {@link org.pahappa.systems.aiquery.querybuilder.CriteriaQueryBuilder}
     * read its value straight back off the same grouped row as the aggregate it divides, with no
     * second query or cross-entity join required. Only SUM/AVG/COUNT make sense as a numerator;
     * MIN/MAX describe an extreme value rather than an amount, and can be non-numeric.
     */
    private ValidatedFieldPath validatePercentageOf(User user, ResolvedEntity entity, AggregationRequest aggregation,
                                                      List<ValidatedFieldPath> groupByFields) {
        String rawName = aggregation.asPercentageOf();
        if (isBlank(rawName)) {
            return null;
        }
        if (groupByFields.isEmpty()) {
            throw new AiQueryValidationException("'asPercentageOf' requires 'groupBy' to be set.");
        }
        if (aggregation.function() != AggregationFunction.SUM && aggregation.function() != AggregationFunction.AVG
                && aggregation.function() != AggregationFunction.COUNT) {
            throw new AiQueryValidationException("'asPercentageOf' is only supported with SUM, AVG, or COUNT.");
        }
        ValidatedFieldPath resolved = resolveFieldPath(user, entity, rawName);
        if (!typeConversionService.isNumeric(resolved.field().getJavaType())) {
            throw new AiQueryValidationException("'asPercentageOf' field '" + resolved.qualifiedName() + "' must be numeric.");
        }
        if (!groupByFields.contains(resolved)) {
            throw new AiQueryValidationException(
                    "'asPercentageOf' field '" + resolved.qualifiedName() + "' must also be listed in 'groupBy'.");
        }
        return resolved;
    }

    private static final class EntityNotFoundSupplier implements java.util.function.Supplier<AiQueryValidationException> {
        private final String entityName;

        EntityNotFoundSupplier(String entityName) {
            this.entityName = entityName;
        }

        @Override
        public AiQueryValidationException get() {
            return new AiQueryValidationException("Unknown entity: '" + entityName + "'.");
        }
    }

    private static final class RelationNotFoundSupplier implements java.util.function.Supplier<AiQueryValidationException> {
        private final String relationName;
        private final String entityName;

        RelationNotFoundSupplier(String relationName, String entityName) {
            this.relationName = relationName;
            this.entityName = entityName;
        }

        @Override
        public AiQueryValidationException get() {
            return new AiQueryValidationException(
                    "Unknown relationship '" + relationName + "' on entity '" + entityName + "'.");
        }
    }

    private static final class RelationFieldNotFoundSupplier implements java.util.function.Supplier<AiQueryValidationException> {
        private final String fieldName;
        private final String relationName;

        RelationFieldNotFoundSupplier(String fieldName, String relationName) {
            this.fieldName = fieldName;
            this.relationName = relationName;
        }

        @Override
        public AiQueryValidationException get() {
            return new AiQueryValidationException(
                    "Field '" + fieldName + "' is not queryable via relationship '" + relationName + "'.");
        }
    }

    private static final class FieldNotFoundSupplier implements java.util.function.Supplier<AiQueryValidationException> {
        private final String fieldName;
        private final String entityName;

        FieldNotFoundSupplier(String fieldName, String entityName) {
            this.fieldName = fieldName;
            this.entityName = entityName;
        }

        @Override
        public AiQueryValidationException get() {
            return new AiQueryValidationException(
                    "Field '" + fieldName + "' is not queryable on entity '" + entityName + "'.");
        }
    }
}
