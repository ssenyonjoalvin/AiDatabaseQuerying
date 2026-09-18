package org.pahappa.systems.aiquery.querybuilder;

import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.DurationUnit;
import org.pahappa.systems.aiquery.dto.SortDirection;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;
import org.pahappa.systems.aiquery.typeconversion.AiTypeConversionService;
import org.pahappa.systems.aiquery.validation.ValidatedAggregation;
import org.pahappa.systems.aiquery.validation.ValidatedFieldPath;
import org.pahappa.systems.aiquery.validation.ValidatedFilter;
import org.pahappa.systems.aiquery.validation.ValidatedFilterGroup;
import org.pahappa.systems.aiquery.validation.ValidatedQuery;
import org.pahappa.systems.aiquery.validation.ValidatedSort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.Tuple;
import javax.persistence.TypedQuery;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Expression;
import javax.persistence.criteria.Join;
import javax.persistence.criteria.JoinType;
import javax.persistence.criteria.Order;
import javax.persistence.criteria.Path;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.persistence.criteria.Selection;
import javax.persistence.metamodel.SingularAttribute;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and executes queries exclusively through the JPA Criteria API ({@link CriteriaBuilder},
 * {@link CriteriaQuery}, {@link Root}, {@link Predicate}, {@link Path}, {@link Selection},
 * {@link Order}) against a {@link ValidatedQuery}. No SQL or JPQL string is ever built or
 * concatenated, and every {@link Path} is obtained through the attribute's typed
 * {@link SingularAttribute} rather than {@code root.get(String)} on an unvalidated name --
 * by the time a request reaches this class, every field has already been resolved against
 * the JPA metamodel by {@code AiQueryValidator}. Only projections of the requested fields
 * are returned; full entities are never loaded or exposed. A {@link ValidatedFieldPath} that
 * reaches into a related entity ({@link ValidatedFieldPath#relation()}) is realized as a single
 * {@code LEFT JOIN} per distinct relationship, built once per query and reused across every
 * selection/filter/sort/aggregation that references it.
 */
@SuppressWarnings({"unchecked", "rawtypes"})
@Service
public class CriteriaQueryBuilder {

    private static final String AGGREGATION_ALIAS = "value";
    private static final String DURATION_START_ALIAS = "__durationStart__";
    private static final String DURATION_END_ALIAS = "__durationEnd__";

    /**
     * Safety cap on distinct groups fetched for a percentage-of aggregation (see {@link
     * #executePercentageAggregate}): the ranked metric there is computed in Java, so page/limit
     * can't be pushed to the database and every candidate group has to be pulled back first.
     */
    private static final int MAX_PERCENTAGE_GROUPS = 1000;

    private final AiQueryProperties properties;

    @Autowired
    public CriteriaQueryBuilder(AiQueryProperties properties) {
        this.properties = properties;
    }

    public static final class ExecutionResult {
        private final List<Map<String, Object>> rows;
        private final boolean hasMore;

        public ExecutionResult(List<Map<String, Object>> rows, boolean hasMore) {
            this.rows = rows;
            this.hasMore = hasMore;
        }

        public List<Map<String, Object>> rows() {
            return rows;
        }

        public boolean hasMore() {
            return hasMore;
        }
    }

    public ExecutionResult executeSelect(EntityManager entityManager, ValidatedQuery query) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());
        Map<String, Join<?, ?>> joins = buildJoins(root, collectFieldPaths(query));

        List<Selection<?>> selections = new ArrayList<Selection<?>>();
        for (ValidatedFieldPath fieldPath : query.selectedFields()) {
            selections.add(pathFor(joins, root, fieldPath).alias(fieldPath.qualifiedName()));
        }
        cq.multiselect(selections);

        applyPredicates(cb, joins, root, cq, query.filters(), query.filterGroups());
        applySorting(cb, joins, root, cq, query.sorts(), null);

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setFirstResult(query.page() * query.pageSize());
        typedQuery.setMaxResults(query.pageSize() + 1);

        List<Tuple> tuples = typedQuery.getResultList();
        boolean hasMore = tuples.size() > query.pageSize();
        List<Tuple> pageTuples = hasMore ? tuples.subList(0, query.pageSize()) : tuples;

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Tuple tuple : pageTuples) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (ValidatedFieldPath fieldPath : query.selectedFields()) {
                row.put(fieldPath.qualifiedName(), tuple.get(fieldPath.qualifiedName()));
            }
            rows.add(row);
        }
        return new ExecutionResult(rows, hasMore);
    }

    public ExecutionResult executeAggregate(EntityManager entityManager, ValidatedQuery query) {
        if (query.aggregation().isDuration()) {
            return executeDurationAggregate(entityManager, query);
        }
        if (query.aggregation().percentageOfField() != null) {
            return executePercentageAggregate(entityManager, query);
        }
        ValidatedAggregation aggregation = query.aggregation();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());
        Map<String, Join<?, ?>> joins = buildJoins(root, collectFieldPaths(query));

        List<Selection<?>> selections = new ArrayList<Selection<?>>();
        List<Expression<?>> groupByExpressions = new ArrayList<Expression<?>>();
        for (ValidatedFieldPath fieldPath : aggregation.groupBy()) {
            Path<?> path = pathFor(joins, root, fieldPath);
            selections.add(path.alias(fieldPath.qualifiedName()));
            groupByExpressions.add(path);
        }

        Expression<?> aggregationExpression = buildNumeratorExpression(cb, joins, root, aggregation);
        selections.add(aggregationExpression.alias(AGGREGATION_ALIAS));
        cq.multiselect(selections);

        applyPredicates(cb, joins, root, cq, query.filters(), query.filterGroups());
        if (!groupByExpressions.isEmpty()) {
            cq.groupBy(groupByExpressions);
        }
        applySorting(cb, joins, root, cq, query.sorts(), aggregationExpression);

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setFirstResult(query.page() * query.pageSize());
        typedQuery.setMaxResults(query.pageSize() + 1);

        List<Tuple> tuples = typedQuery.getResultList();
        boolean hasMore = tuples.size() > query.pageSize();
        List<Tuple> pageTuples = hasMore ? tuples.subList(0, query.pageSize()) : tuples;

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Tuple tuple : pageTuples) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (ValidatedFieldPath fieldPath : aggregation.groupBy()) {
                row.put(fieldPath.qualifiedName(), tuple.get(fieldPath.qualifiedName()));
            }
            row.put(AGGREGATION_ALIAS, tuple.get(AGGREGATION_ALIAS));
            rows.add(row);
        }
        return new ExecutionResult(rows, hasMore);
    }

    /**
     * Runs a {@code groupBy} aggregation whose ranked metric is a percentage -- the aggregate
     * "value" divided by one of the grouped fields (see {@link ValidatedAggregation#percentageOfField()})
     * -- rather than the raw aggregate. That division, the sort, and the paging all have to happen
     * in Java rather than in SQL: every candidate group is fetched (up to {@link
     * #MAX_PERCENTAGE_GROUPS}), the percentage is computed per group, groups whose denominator is
     * null or zero are dropped (a percentage against nothing is undefined), and only then are the
     * rows sorted and sliced to the requested page.
     */
    private ExecutionResult executePercentageAggregate(EntityManager entityManager, ValidatedQuery query) {
        ValidatedAggregation aggregation = query.aggregation();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());
        Map<String, Join<?, ?>> joins = buildJoins(root, collectFieldPaths(query));

        List<Selection<?>> selections = new ArrayList<Selection<?>>();
        List<Expression<?>> groupByExpressions = new ArrayList<Expression<?>>();
        for (ValidatedFieldPath fieldPath : aggregation.groupBy()) {
            Path<?> path = pathFor(joins, root, fieldPath);
            selections.add(path.alias(fieldPath.qualifiedName()));
            groupByExpressions.add(path);
        }

        Expression<?> aggregationExpression = buildNumeratorExpression(cb, joins, root, aggregation);
        selections.add(aggregationExpression.alias(AGGREGATION_ALIAS));
        cq.multiselect(selections);

        applyPredicates(cb, joins, root, cq, query.filters(), query.filterGroups());
        cq.groupBy(groupByExpressions);

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setMaxResults(MAX_PERCENTAGE_GROUPS);
        List<Tuple> tuples = typedQuery.getResultList();

        String denominatorAlias = aggregation.percentageOfField().qualifiedName();
        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Tuple tuple : tuples) {
            Number numerator = (Number) tuple.get(AGGREGATION_ALIAS);
            Number denominator = (Number) tuple.get(denominatorAlias);
            if (numerator == null || denominator == null || denominator.doubleValue() == 0.0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (ValidatedFieldPath fieldPath : aggregation.groupBy()) {
                row.put(fieldPath.qualifiedName(), tuple.get(fieldPath.qualifiedName()));
            }
            row.put(AGGREGATION_ALIAS, roundToTwoDecimals(numerator.doubleValue() / denominator.doubleValue() * 100.0));
            rows.add(row);
        }

        sortRows(rows, query.sorts());

        int firstResult = query.page() * query.pageSize();
        if (firstResult >= rows.size()) {
            return new ExecutionResult(Collections.<Map<String, Object>>emptyList(), false);
        }
        int lastExclusive = Math.min(rows.size(), firstResult + query.pageSize());
        boolean hasMore = rows.size() > lastExclusive;
        return new ExecutionResult(new ArrayList<Map<String, Object>>(rows.subList(firstResult, lastExclusive)), hasMore);
    }

    /**
     * Runs a {@code groupBy} aggregation whose metric is an elapsed duration between two
     * date/temporal fields (see {@link ValidatedAggregation#durationStart()}/{@link
     * ValidatedAggregation#durationEnd()}) rather than a plain column aggregate -- there is no
     * portable, dialect-independent date-diff expression in JPA Criteria, so each candidate
     * row's raw start/end values are fetched (up to {@link AiQueryProperties#getMaxDurationRows()})
     * and the per-row duration, per-group accumulation, sort, and paging all happen in Java,
     * the same way {@link #executePercentageAggregate} already computes its percentage in Java.
     */
    private ExecutionResult executeDurationAggregate(EntityManager entityManager, ValidatedQuery query) {
        ValidatedAggregation aggregation = query.aggregation();
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<?> root = cq.from(query.entity().getJavaType());
        Map<String, Join<?, ?>> joins = buildJoins(root, collectFieldPaths(query));

        List<ValidatedFieldPath> groupByFields = aggregation.groupBy();
        List<Selection<?>> selections = new ArrayList<Selection<?>>();
        for (ValidatedFieldPath fieldPath : groupByFields) {
            selections.add(pathFor(joins, root, fieldPath).alias(fieldPath.qualifiedName()));
        }
        selections.add(pathFor(joins, root, aggregation.durationStart()).alias(DURATION_START_ALIAS));
        selections.add(pathFor(joins, root, aggregation.durationEnd()).alias(DURATION_END_ALIAS));
        cq.multiselect(selections);

        applyPredicates(cb, joins, root, cq, query.filters(), query.filterGroups());

        TypedQuery<Tuple> typedQuery = entityManager.createQuery(cq);
        typedQuery.setMaxResults(properties.getMaxDurationRows());
        List<Tuple> tuples = typedQuery.getResultList();

        double unitMillis = millisPerUnit(aggregation.durationUnit());
        Map<List<Object>, DurationAccumulator> accumulators = new LinkedHashMap<List<Object>, DurationAccumulator>();
        for (Tuple tuple : tuples) {
            Long startMillis = toEpochMillis(tuple.get(DURATION_START_ALIAS));
            Long endMillis = toEpochMillis(tuple.get(DURATION_END_ALIAS));
            if (startMillis == null || endMillis == null || endMillis < startMillis) {
                continue;
            }
            List<Object> key = new ArrayList<Object>();
            for (ValidatedFieldPath fieldPath : groupByFields) {
                key.add(tuple.get(fieldPath.qualifiedName()));
            }
            DurationAccumulator accumulator = accumulators.get(key);
            if (accumulator == null) {
                accumulator = new DurationAccumulator();
                accumulators.put(key, accumulator);
            }
            accumulator.add((endMillis - startMillis) / unitMillis);
        }

        List<Map<String, Object>> rows = new ArrayList<Map<String, Object>>();
        for (Map.Entry<List<Object>, DurationAccumulator> entry : accumulators.entrySet()) {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            for (int i = 0; i < groupByFields.size(); i++) {
                row.put(groupByFields.get(i).qualifiedName(), entry.getKey().get(i));
            }
            row.put(AGGREGATION_ALIAS, roundToTwoDecimals(entry.getValue().result(aggregation.function())));
            rows.add(row);
        }

        sortRows(rows, query.sorts());

        int firstResult = query.page() * query.pageSize();
        if (firstResult >= rows.size()) {
            return new ExecutionResult(Collections.<Map<String, Object>>emptyList(), false);
        }
        int lastExclusive = Math.min(rows.size(), firstResult + query.pageSize());
        boolean hasMore = rows.size() > lastExclusive;
        return new ExecutionResult(new ArrayList<Map<String, Object>>(rows.subList(firstResult, lastExclusive)), hasMore);
    }

    private double millisPerUnit(DurationUnit unit) {
        switch (unit) {
            case SECONDS: return 1000.0;
            case MINUTES: return 60000.0;
            case HOURS: return 3600000.0;
            case DAYS:
            default: return 86400000.0;
        }
    }

    /** Supports the temporal types actually produced by JPA attributes: legacy {@code java.util.Date}/subclasses and {@code java.time} LocalDate/LocalDateTime/Instant. */
    private Long toEpochMillis(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof java.util.Date) {
            return ((java.util.Date) value).getTime();
        }
        if (value instanceof java.time.LocalDateTime) {
            return ((java.time.LocalDateTime) value).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof java.time.LocalDate) {
            return ((java.time.LocalDate) value).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        }
        if (value instanceof java.time.Instant) {
            return ((java.time.Instant) value).toEpochMilli();
        }
        return null;
    }

    /** Running sum/count/min/max for one group's durations, resolved to a final value by {@link #result}. */
    private static final class DurationAccumulator {
        private double sum = 0;
        private long count = 0;
        private double min = Double.POSITIVE_INFINITY;
        private double max = Double.NEGATIVE_INFINITY;

        void add(double value) {
            sum += value;
            count++;
            if (value < min) {
                min = value;
            }
            if (value > max) {
                max = value;
            }
        }

        double result(AggregationFunction function) {
            if (count == 0) {
                return 0;
            }
            switch (function) {
                case AVG: return sum / count;
                case SUM: return sum;
                case MIN: return min;
                case MAX: return max;
                default: throw new AiQueryValidationException("Unsupported aggregation function for a duration: " + function);
            }
        }
    }

    private void sortRows(List<Map<String, Object>> rows, List<ValidatedSort> sorts) {
        if (sorts.isEmpty()) {
            return;
        }
        Collections.sort(rows, new Comparator<Map<String, Object>>() {
            public int compare(Map<String, Object> a, Map<String, Object> b) {
                for (ValidatedSort sort : sorts) {
                    String key = sort.field() == null ? AGGREGATION_ALIAS : sort.field().qualifiedName();
                    int cmp = compareValues(a.get(key), b.get(key));
                    if (cmp != 0) {
                        return sort.direction() == SortDirection.ASC ? cmp : -cmp;
                    }
                }
                return 0;
            }
        });
    }

    @SuppressWarnings("unchecked")
    private int compareValues(Object a, Object b) {
        if (a == null) {
            return b == null ? 0 : -1;
        }
        if (b == null) {
            return 1;
        }
        if (a instanceof Comparable && b.getClass().isInstance(a)) {
            return ((Comparable<Object>) a).compareTo(b);
        }
        return a.toString().compareTo(b.toString());
    }

    private double roundToTwoDecimals(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    /** Every distinct field path referenced anywhere in the query, for join collection. */
    private List<ValidatedFieldPath> collectFieldPaths(ValidatedQuery query) {
        List<ValidatedFieldPath> paths = new ArrayList<ValidatedFieldPath>();
        paths.addAll(query.selectedFields());
        for (ValidatedFilter filter : query.filters()) {
            paths.add(filter.field());
        }
        for (ValidatedFilterGroup group : query.filterGroups()) {
            for (ValidatedFilter filter : group.anyOf()) {
                paths.add(filter.field());
            }
        }
        for (ValidatedSort sort : query.sorts()) {
            if (sort.field() != null) {
                paths.add(sort.field());
            }
        }
        if (query.aggregation() != null) {
            if (query.aggregation().field() != null) {
                paths.add(query.aggregation().field());
            }
            if (query.aggregation().subtractField() != null) {
                paths.add(query.aggregation().subtractField());
            }
            if (query.aggregation().durationStart() != null) {
                paths.add(query.aggregation().durationStart());
            }
            if (query.aggregation().durationEnd() != null) {
                paths.add(query.aggregation().durationEnd());
            }
            paths.addAll(query.aggregation().groupBy());
        }
        return paths;
    }

    /** One LEFT JOIN per distinct relationship referenced, so it's built at most once per query. */
    private Map<String, Join<?, ?>> buildJoins(Root<?> root, List<ValidatedFieldPath> fieldPaths) {
        Map<String, Join<?, ?>> joins = new LinkedHashMap<String, Join<?, ?>>();
        for (ValidatedFieldPath fieldPath : fieldPaths) {
            if (fieldPath.isDirect()) {
                continue;
            }
            String relationName = fieldPath.relation().getName();
            if (!joins.containsKey(relationName)) {
                joins.put(relationName, joinFor(root, fieldPath.relation()));
            }
        }
        return joins;
    }

    private Join<?, ?> joinFor(Root<?> root, ResolvedRelation relation) {
        Root rawRoot = root;
        SingularAttribute rawAttribute = relation.getAttribute();
        return rawRoot.join(rawAttribute, JoinType.LEFT);
    }

    /**
     * Every {@code filters} entry is AND'd together as before. Each {@code filterGroups}
     * entry contributes one additional AND'd predicate whose own {@code anyOf} conditions are
     * OR'd together -- e.g. a single group of two filters expresses "field A = X OR field B = Y".
     */
    private void applyPredicates(CriteriaBuilder cb, Map<String, Join<?, ?>> joins, Root<?> root, CriteriaQuery<?> cq,
                                  List<ValidatedFilter> filters, List<ValidatedFilterGroup> filterGroups) {
        List<Predicate> predicates = new ArrayList<Predicate>();
        for (ValidatedFilter filter : filters) {
            predicates.add(buildPredicate(cb, joins, root, filter));
        }
        for (ValidatedFilterGroup group : filterGroups) {
            List<Predicate> orPredicates = new ArrayList<Predicate>();
            for (ValidatedFilter filter : group.anyOf()) {
                orPredicates.add(buildPredicate(cb, joins, root, filter));
            }
            predicates.add(cb.or(orPredicates.toArray(new Predicate[0])));
        }
        if (predicates.isEmpty()) {
            return;
        }
        cq.where(predicates.toArray(new Predicate[0]));
    }

    /**
     * {@code aggregationExpression} is only non-null from {@link #executeAggregate}, and is what
     * a {@link ValidatedSort} with a null field (sorting by the aggregated "value") orders by.
     */
    private void applySorting(CriteriaBuilder cb, Map<String, Join<?, ?>> joins, Root<?> root, CriteriaQuery<?> cq,
                               List<ValidatedSort> sorts, Expression<?> aggregationExpression) {
        if (sorts.isEmpty()) {
            return;
        }
        List<Order> orders = new ArrayList<Order>();
        for (ValidatedSort sort : sorts) {
            Expression<?> expression = sort.field() == null ? aggregationExpression : pathFor(joins, root, sort.field());
            orders.add(sort.direction() == SortDirection.ASC ? cb.asc(expression) : cb.desc(expression));
        }
        cq.orderBy(orders);
    }

    private Predicate buildPredicate(CriteriaBuilder cb, Map<String, Join<?, ?>> joins, Root<?> root, ValidatedFilter filter) {
        Path path = pathFor(joins, root, filter.field());
        Object value = filter.value();
        switch (filter.operator()) {
            case EQUALS:
                return cb.equal(path, value);
            case NOT_EQUALS:
                return cb.notEqual(path, value);
            case GREATER_THAN:
                return cb.greaterThan((Path<Comparable>) path, (Comparable) value);
            case GREATER_THAN_OR_EQUAL:
                return cb.greaterThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
            case LESS_THAN:
                return cb.lessThan((Path<Comparable>) path, (Comparable) value);
            case LESS_THAN_OR_EQUAL:
                return cb.lessThanOrEqualTo((Path<Comparable>) path, (Comparable) value);
            case LIKE:
                return cb.like(cb.lower((Path<String>) path), likePattern((String) value, true, true), '\\');
            case STARTS_WITH:
                return cb.like(cb.lower((Path<String>) path), likePattern((String) value, false, true), '\\');
            case ENDS_WITH:
                return cb.like(cb.lower((Path<String>) path), likePattern((String) value, true, false), '\\');
            case IS_NULL:
                return cb.isNull(path);
            case IS_NOT_NULL:
                return cb.isNotNull(path);
            case IN:
                return path.in((List) value);
            default:
                throw new AiQueryValidationException("Unsupported operator: " + filter.operator());
        }
    }

    private String likePattern(String raw, boolean leadingWildcard, boolean trailingWildcard) {
        String escaped = raw.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return (leadingWildcard ? "%" : "") + escaped + (trailingWildcard ? "%" : "");
    }

    /**
     * The aggregation's numerator: {@code function(field)}, or {@code function(field) -
     * function(subtractField)} when {@link ValidatedAggregation#subtractField()} is set --
     * for entities that expose a total and a remaining balance rather than an amount already
     * used, there is no single column to aggregate directly.
     */
    private Expression<?> buildNumeratorExpression(CriteriaBuilder cb, Map<String, Join<?, ?>> joins, Root<?> root,
                                                     ValidatedAggregation aggregation) {
        Expression<?> primary = buildAggregationExpression(cb, joins, root, aggregation.function(), aggregation.field());
        if (aggregation.subtractField() == null) {
            return primary;
        }
        Expression<?> toSubtract = buildAggregationExpression(cb, joins, root, aggregation.function(), aggregation.subtractField());
        return cb.diff((Expression<Number>) primary, (Expression<Number>) toSubtract);
    }

    private Expression<?> buildAggregationExpression(CriteriaBuilder cb, Map<String, Join<?, ?>> joins, Root<?> root,
                                                       AggregationFunction function, ValidatedFieldPath fieldPath) {
        switch (function) {
            case COUNT:
                return fieldPath == null ? cb.count(root) : cb.count(pathFor(joins, root, fieldPath));
            case SUM:
                return cb.sum((Expression<Number>) pathFor(joins, root, fieldPath));
            case AVG:
                return cb.avg((Expression<Number>) pathFor(joins, root, fieldPath));
            case MIN:
                return minMax(cb, pathFor(joins, root, fieldPath), fieldPath.field().getJavaType(), false);
            case MAX:
                return minMax(cb, pathFor(joins, root, fieldPath), fieldPath.field().getJavaType(), true);
            default:
                throw new AiQueryValidationException("Unsupported aggregation function: " + function);
        }
    }

    private Expression<?> minMax(CriteriaBuilder cb, Path<?> path, Class<?> javaType, boolean max) {
        Class<?> boxed = AiTypeConversionService.box(javaType);
        if (Number.class.isAssignableFrom(boxed)) {
            Expression<Number> numeric = (Expression<Number>) path;
            return max ? cb.max(numeric) : cb.min(numeric);
        }
        if (Comparable.class.isAssignableFrom(boxed)) {
            Expression<Comparable> comparable = (Expression<Comparable>) path;
            return max ? cb.greatest(comparable) : cb.least(comparable);
        }
        throw new AiQueryValidationException("MIN/MAX is not supported for this field type.");
    }

    private Path<?> pathFor(Map<String, Join<?, ?>> joins, Root<?> root, ValidatedFieldPath fieldPath) {
        SingularAttribute rawAttribute = fieldPath.field().getAttribute();
        if (fieldPath.isDirect()) {
            Root rawRoot = root;
            return rawRoot.get(rawAttribute);
        }
        Join rawJoin = joins.get(fieldPath.relation().getName());
        return rawJoin.get(rawAttribute);
    }
}
