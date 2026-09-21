package org.pahappa.systems.aiquery.audit;

import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.sers.webutils.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Structured audit trail for every AI-driven database access: who, what entity, which
 * fields, what operation, how many filters/results, how long it took, and whether it
 * succeeded. Deliberately never logs filter/sort *values* -- only field names and counts --
 * since those may contain sensitive search terms.
 */
@Component
public class AiQueryAuditLogger {

    private static final Logger AUDIT_LOG = LoggerFactory.getLogger("AI_QUERY_AUDIT");

    public void logListEntities(User user, int count) {
        AUDIT_LOG.info("user={} operation=LIST_ENTITIES outcome=SUCCESS count={}", username(user), count);
    }

    public void logDescribeEntity(User user, String entity, int fieldCount) {
        AUDIT_LOG.info("user={} entity={} operation=DESCRIBE outcome=SUCCESS fieldCount={}",
                new Object[]{username(user), entity, fieldCount});
    }

    public void logQuerySuccess(String requestId, User user, String entity, List<String> fields,
                                 List<FilterCriterion> filters, List<SortCriterion> sort, int page, int pageSize,
                                 AggregationRequest aggregation, int resultCount, long durationMs) {
        logQuerySuccess(requestId, user, entity, fields, filters, Collections.<FilterGroup>emptyList(), sort, page,
                pageSize, aggregation, resultCount, durationMs);
    }

    /**
     * {@code requestId} correlates every row logged for one user question: the agentic loop can
     * run up to several of these per question (one per {@code query_db} tool call), and without a
     * shared id they'd be indistinguishable in the log from unrelated separate questions by the
     * same user.
     */
    public void logQuerySuccess(String requestId, User user, String entity, List<String> fields,
                                 List<FilterCriterion> filters, List<FilterGroup> filterGroups,
                                 List<SortCriterion> sort, int page, int pageSize, AggregationRequest aggregation,
                                 int resultCount, long durationMs) {
        AUDIT_LOG.info("requestId={} user={} entity={} operation=QUERY outcome=SUCCESS fields={} filterFields={} " +
                        "filterCount={} filterGroupCount={} sortFields={} page={} pageSize={} aggregation={} " +
                        "resultCount={} durationMs={}",
                new Object[]{requestId, username(user), entity, fields,
                        fieldNames(filters, new FieldNameExtractor<FilterCriterion>() {
                    public String extract(FilterCriterion item) {
                        return item.field();
                    }
                }), sizeOf(filters), sizeOf(filterGroups),
                        fieldNames(sort, new FieldNameExtractor<SortCriterion>() {
                            public String extract(SortCriterion item) {
                                return item.field();
                            }
                        }), page, pageSize, describeAggregation(aggregation), resultCount, durationMs});
    }

    public void logFailure(String requestId, User user, String entity, String operation, String phase, long durationMs) {
        AUDIT_LOG.warn("requestId={} user={} entity={} operation={} outcome=FAILURE phase={} durationMs={}",
                new Object[]{requestId, username(user), entity, operation, phase, durationMs});
    }

    private String username(User user) {
        return user == null ? "unknown" : user.getUsername();
    }

    private interface FieldNameExtractor<T> {
        String extract(T item);
    }

    private <T> List<String> fieldNames(List<T> items, FieldNameExtractor<T> nameExtractor) {
        if (items == null) {
            return Collections.emptyList();
        }
        List<String> names = new ArrayList<String>();
        for (T item : items) {
            names.add(nameExtractor.extract(item));
        }
        return names;
    }

    private int sizeOf(List<?> items) {
        return items == null ? 0 : items.size();
    }

    private String describeAggregation(AggregationRequest aggregation) {
        if (aggregation == null) {
            return "none";
        }
        boolean isDuration = aggregation.durationStartField() != null && aggregation.durationEndField() != null;
        String base;
        if (isDuration) {
            String unit = aggregation.durationUnit() == null ? "DAYS" : aggregation.durationUnit();
            base = aggregation.function() + " duration(" + aggregation.durationStartField() + "->"
                    + aggregation.durationEndField() + ", " + unit + ")";
        } else {
            base = aggregation.field() == null
                    ? aggregation.function().name()
                    : aggregation.function() + ":" + aggregation.field();
        }
        if (aggregation.groupBy() != null && !aggregation.groupBy().isEmpty()) {
            return base + " groupBy=" + aggregation.groupBy();
        }
        return base;
    }
}
