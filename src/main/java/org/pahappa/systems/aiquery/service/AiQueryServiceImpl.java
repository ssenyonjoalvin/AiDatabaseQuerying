package org.pahappa.systems.aiquery.service;

import org.pahappa.systems.aiquery.audit.AiQueryAuditLogger;
import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.dto.QueryableEntityDescriptor;
import org.pahappa.systems.aiquery.dto.QueryableEntitySummary;
import org.pahappa.systems.aiquery.dto.QueryableFieldDescriptor;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;
import org.pahappa.systems.aiquery.exception.AiQueryExecutionException;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.metadata.AiEntityMetadataService;
import org.pahappa.systems.aiquery.metadata.ResolvedEntity;
import org.pahappa.systems.aiquery.metadata.ResolvedField;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;
import org.pahappa.systems.aiquery.querybuilder.CriteriaQueryBuilder;
import org.pahappa.systems.aiquery.security.AiDatabaseAuthorizationService;
import org.pahappa.systems.aiquery.typeconversion.AiTypeConversionService;
import org.pahappa.systems.aiquery.validation.AiQueryValidator;
import org.pahappa.systems.aiquery.validation.ValidatedFieldPath;
import org.pahappa.systems.aiquery.validation.ValidatedQuery;
import org.sers.webutils.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates the generic, read-only AI query capability: validate (which independently
 * re-checks authorization, {@code @AiNotQueryable}, operators, types, and limits) then
 * build-and-execute through {@link CriteriaQueryBuilder}, then audit-log, catching any
 * unexpected persistence failure and translating it into a safe, generic error instead of
 * letting Hibernate/JDBC detail reach the caller. Every method is read-only by
 * construction: there is no code path here capable of an insert, update, or delete.
 */
@Service
public class AiQueryServiceImpl implements AiQueryService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AiQueryServiceImpl.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;
    private final AiQueryValidator validator;
    private final CriteriaQueryBuilder criteriaQueryBuilder;
    private final AiQueryAuditLogger auditLogger;
    private final AiTypeConversionService typeConversionService;
    private final NaturalLanguageQueryTranslator naturalLanguageQueryTranslator;

    @Autowired
    public AiQueryServiceImpl(AiEntityMetadataService metadataService,
                           AiDatabaseAuthorizationService authorizationService,
                           AiQueryValidator validator,
                           CriteriaQueryBuilder criteriaQueryBuilder,
                           AiQueryAuditLogger auditLogger,
                           AiTypeConversionService typeConversionService,
                           NaturalLanguageQueryTranslator naturalLanguageQueryTranslator) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
        this.validator = validator;
        this.criteriaQueryBuilder = criteriaQueryBuilder;
        this.auditLogger = auditLogger;
        this.typeConversionService = typeConversionService;
        this.naturalLanguageQueryTranslator = naturalLanguageQueryTranslator;
    }

    public List<QueryableEntitySummary> listQueryableEntities(User user) {
        List<QueryableEntitySummary> entities = new ArrayList<QueryableEntitySummary>();
        for (ResolvedEntity entity : metadataService.listEntities()) {
            if (authorizationService.canAccessEntity(user, entity.getEntityName())) {
                entities.add(new QueryableEntitySummary(entity.getEntityName()));
            }
        }
        java.util.Collections.sort(entities, new Comparator<QueryableEntitySummary>() {
            public int compare(QueryableEntitySummary a, QueryableEntitySummary b) {
                return a.entity().compareTo(b.entity());
            }
        });
        auditLogger.logListEntities(user, entities.size());
        return entities;
    }

    /**
     * Translates a natural-language question into a structured query via
     * {@link NaturalLanguageQueryTranslator}, then executes it through the exact same
     * validation/authorization/audit path as {@link #queryEntity}. The model only ever
     * chooses which already-authorized entity/field/operator/value to use -- it cannot
     * bypass validation, since the translated result is re-validated from scratch.
     */
    @Transactional(readOnly = true)
    public QueryResult queryFromNaturalLanguage(User user, String naturalLanguageQuery) {

        TranslatedQueryRequest translated = naturalLanguageQueryTranslator.translate(user, naturalLanguageQuery);
        return queryEntity(user, translated.entityName(), translated.fields(), translated.filters(),
                translated.filterGroups(), translated.sort(), translated.page(), translated.pageSize(),
                translated.aggregation());
    }

    public QueryableEntityDescriptor describeQueryableEntity(User user, String entityName) {
        ResolvedEntity entity = validator.validateEntityAccess(user, entityName);
        List<QueryableFieldDescriptor> fields = new ArrayList<QueryableFieldDescriptor>();
        for (ResolvedField field : entity.getAllFields()) {
            if (authorizationService.canAccessField(user, entity.getEntityName(), field.getName())) {
                fields.add(descriptorFor(field.getName(), field));
            }
        }
        // One-level relationship fields (e.g. "initiatorOfRequisition.username"), gated both on
        // the relationship itself and on the specific target field -- same rule the AI query
        // path enforces in AiQueryValidator.resolveFieldPath.
        for (ResolvedRelation relation : entity.getAllRelations()) {
            if (!authorizationService.canAccessField(user, entity.getEntityName(), relation.getName())) {
                continue;
            }
            ResolvedEntity targetEntity = relation.getTargetEntity();
            for (ResolvedField targetField : targetEntity.getAllFields()) {
                if (authorizationService.canAccessField(user, targetEntity.getEntityName(), targetField.getName())) {
                    fields.add(descriptorFor(relation.getName() + "." + targetField.getName(), targetField));
                }
            }
        }
        java.util.Collections.sort(fields, new Comparator<QueryableFieldDescriptor>() {
            public int compare(QueryableFieldDescriptor a, QueryableFieldDescriptor b) {
                return a.name().compareTo(b.name());
            }
        });
        QueryableEntityDescriptor descriptor = new QueryableEntityDescriptor(entity.getEntityName(), fields);
        auditLogger.logDescribeEntity(user, entity.getEntityName(), fields.size());
        return descriptor;
    }

    private QueryableFieldDescriptor descriptorFor(String qualifiedName, ResolvedField field) {
        return new QueryableFieldDescriptor(
                qualifiedName,
                typeConversionService.displayName(field.getJavaType()),
                field.isFilterable(),
                field.isSortable(),
                field.getSupportedOperators(),
                field.getAllowedValues());
    }

    @Transactional(readOnly = true)
    public QueryResult queryEntity(User user, String entityName, List<String> fields,
                                    List<FilterCriterion> filters, List<SortCriterion> sort,
                                    Integer page, Integer pageSize, AggregationRequest aggregation) {
        return queryEntity(user, entityName, fields, filters, java.util.Collections.<FilterGroup>emptyList(), sort,
                page, pageSize, aggregation);
    }

    @Transactional(readOnly = true)
    public QueryResult queryEntity(User user, String entityName, List<String> fields,
                                    List<FilterCriterion> filters, List<FilterGroup> filterGroups,
                                    List<SortCriterion> sort,
                                    Integer page, Integer pageSize, AggregationRequest aggregation) {
        return queryEntity(user, entityName, fields, filters, filterGroups, sort, page, pageSize, aggregation,
                java.util.UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public QueryResult queryEntity(User user, String entityName, List<String> fields,
                                    List<FilterCriterion> filters, List<FilterGroup> filterGroups,
                                    List<SortCriterion> sort,
                                    Integer page, Integer pageSize, AggregationRequest aggregation, String requestId) {
        long start = System.nanoTime();
        try {
            ValidatedQuery validated = validator.validateQuery(user, entityName, fields, filters, filterGroups,
                    sort, page, pageSize, aggregation);

            CriteriaQueryBuilder.ExecutionResult result = validated.aggregation() != null
                    ? criteriaQueryBuilder.executeAggregate(entityManager, validated)
                    : criteriaQueryBuilder.executeSelect(entityManager, validated);

            List<String> outputFields = new ArrayList<String>();
            if (validated.aggregation() != null) {
                for (ValidatedFieldPath groupByField : validated.aggregation().groupBy()) {
                    outputFields.add(groupByField.qualifiedName());
                }
                outputFields.add("value");
            } else {
                for (ValidatedFieldPath selectedField : validated.selectedFields()) {
                    outputFields.add(selectedField.qualifiedName());
                }
            }

            QueryResult queryResult = new QueryResult(
                    validated.entity().getEntityName(),
                    outputFields,
                    result.rows(),
                    validated.page(),
                    validated.pageSize(),
                    result.rows().size(),
                    result.hasMore(),
                    aggregation);

            auditLogger.logQuerySuccess(requestId, user, validated.entity().getEntityName(), outputFields, filters,
                    filterGroups, sort, validated.page(), validated.pageSize(), aggregation, result.rows().size(),
                    durationMs(start));
            return queryResult;
        } catch (AiQueryValidationException ex) {
            auditLogger.logFailure(requestId, user, entityName, "QUERY", "VALIDATION", durationMs(start));
            throw ex;
        } catch (RuntimeException ex) {
            LOGGER.error("Unexpected error executing AI query for entity '{}'", entityName, ex);
            auditLogger.logFailure(requestId, user, entityName, "QUERY", "EXECUTION", durationMs(start));
            throw new AiQueryExecutionException("The query could not be completed. Please adjust the request and try again.");
        }
    }

    private long durationMs(long startNanos) {
        return (System.nanoTime() - startNanos) / 1_000_000;
    }
}
