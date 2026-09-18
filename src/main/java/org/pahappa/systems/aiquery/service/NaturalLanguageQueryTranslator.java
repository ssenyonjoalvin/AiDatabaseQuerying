package org.pahappa.systems.aiquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pahappa.systems.aiquery.client.AiChatClient;
import org.pahappa.systems.aiquery.config.AiQueryProperties;
import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.FilterOperator;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.pahappa.systems.aiquery.dto.SortDirection;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.metadata.AiEntityMetadataService;
import org.pahappa.systems.aiquery.metadata.ResolvedEntity;
import org.pahappa.systems.aiquery.metadata.ResolvedField;
import org.pahappa.systems.aiquery.metadata.ResolvedRelation;
import org.pahappa.systems.aiquery.security.AiDatabaseAuthorizationService;
import org.sers.webutils.model.security.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Translates a natural-language question into a {@link TranslatedQueryRequest} by describing
 * the caller's accessible entities/fields to the model and asking it to answer in a fixed
 * JSON shape. The model is never trusted with anything beyond field/operator/value selection:
 * every entity and field it names still has to pass
 * {@link org.pahappa.systems.aiquery.validation.AiQueryValidator} exactly like a request
 * submitted directly through {@link AiQueryService#queryEntity}, and this class performs no
 * database access itself.
 */
@Service
public class NaturalLanguageQueryTranslator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageQueryTranslator.class);

    private static final String RESPONSE_CONTRACT =
            "Respond with ONLY a single JSON object (no markdown fences, no commentary) matching this shape:\n" +
            "{\n" +
            "  \"entity\": \"<entity name>\",\n" +
            "  \"fields\": [\"<field name>\", ...] or null,\n" +
            "  \"filters\": [{\"field\": \"<name>\", \"operator\": \"<OPERATOR>\", \"value\": <scalar, array, or null>}],\n" +
            "  \"filterGroups\": [[{\"field\": \"<name>\", \"operator\": \"<OPERATOR>\", \"value\": <scalar, array, or null>}, ...], ...] " +
            "or null,\n" +
            "  \"sort\": [{\"field\": \"<name>\", \"direction\": \"ASC or DESC\"}],\n" +
            "  \"page\": <integer or null>,\n" +
            "  \"pageSize\": <integer or null>,\n" +
            "  \"aggregation\": {\"function\": \"COUNT, SUM, AVG, MIN, or MAX\", \"field\": \"<name> or null\", " +
            "\"groupBy\": [\"<name>\", ...], \"asPercentageOf\": \"<name> or null\", \"subtractField\": \"<name> or null\", " +
            "\"durationStartField\": \"<name> or null\", \"durationEndField\": \"<name> or null\", " +
            "\"durationUnit\": \"SECONDS, MINUTES, HOURS, or DAYS, or null\"} or null\n" +
            "}\n" +
            "Valid operator values: EQUALS, NOT_EQUALS, GREATER_THAN, GREATER_THAN_OR_EQUAL, LESS_THAN, " +
            "LESS_THAN_OR_EQUAL, LIKE, STARTS_WITH, ENDS_WITH, IS_NULL, IS_NOT_NULL, IN.\n" +
            "A filter value for a date/time-typed field must be a string in ISO-8601 form, " +
            "\"yyyy-MM-dd\" (or \"yyyy-MM-ddTHH:mm:ss\" if a time is relevant) -- never a relative phrase " +
            "like \"this year\"; compute the concrete date(s) yourself from today's date given below.\n" +
            "Only use entity and field names exactly as listed below -- never invent one. A field listed with " +
            "a dot, e.g. \"initiatorOfRequisition.username\", reaches one related entity one level deep -- " +
            "copy it verbatim, including the dot; never invent further nesting beyond what is listed. " +
            "\"fields\" is only for a plain select and should be null when \"aggregation\" is set. When " +
            "\"aggregation\" is set, \"sort\" fields must be either the reserved name \"value\" (the " +
            "aggregated result -- use this for questions like \"which X has the most/highest/least/lowest " +
            "Y\", sorted DESC or ASC and combined with a small \"pageSize\" such as 1 to get a top-N answer) " +
            "or one of the \"groupBy\" field names; no other field may be sorted on while aggregating. " +
            "\"asPercentageOf\" is for questions asking what percentage/share/proportion of some numeric " +
            "amount has been used or reached, per group -- e.g. \"which project has used the biggest " +
            "percentage of its allocated budget\" or \"what percentage of the annual budget has been used\". " +
            "Set \"function\" to SUM (or AVG/COUNT) with \"field\" as the amount being used/consumed, put the " +
            "numeric ceiling/allocation/total field in \"groupBy\" alongside whatever identifies the group " +
            "(e.g. a name, or the entity's own \"id\" if there is naturally only one row per group, such as a " +
            "single budget record), and set \"asPercentageOf\" to that exact same ceiling field name (it must " +
            "also appear in \"groupBy\"); \"value\" then means the computed percentage, so sort DESC on " +
            "\"value\" with a small \"pageSize\" for a \"biggest/highest percentage\" question. Leave " +
            "\"asPercentageOf\" null for a plain aggregation. \"subtractField\" is for entities that track a " +
            "total and a remaining balance instead of an amount already used (e.g. a budget's total amount " +
            "plus a remaining balance field, with no separate \"amount used\" field) -- only valid with " +
            "SUM/AVG, it makes the aggregated numerator \"field\" minus \"subtractField\" (e.g. field = the " +
            "total, subtractField = the remaining balance, so the numerator becomes the amount actually " +
            "used), and combines with \"asPercentageOf\" set to that same total field to get a percentage " +
            "used. Leave \"subtractField\" null when the entity already has a direct \"amount used\" field. " +
            "\"filterGroups\": use for \"or\" between two conditions (\"filters\" is always AND). Each inner " +
            "array is OR'd internally; groups and \"filters\" are AND'd together. E.g. \"disbursed or " +
            "rejected\" = one filterGroups entry with both conditions. Null/empty otherwise.\n" +
            "\"durationStartField\"/\"durationEndField\": use for \"how long\"/\"slowest\"/\"bottleneck\" " +
            "questions -- aggregates elapsed time between two real listed date fields instead of a numeric " +
            "\"field\". function: AVG/SUM/MIN/MAX (never COUNT); start=earlier date, end=later date; " +
            "groupBy=the field to compare by; leave \"field\"/\"subtractField\" null; durationUnit defaults " +
            "DAYS.\n" +
            "If the question cannot be answered with the listed entities/fields, respond with entity set to " +
            "null and every other field empty.";

    private static final String ENTITY_LINKING_INSTRUCTIONS =
            "Respond with ONLY a JSON array of entity names, copied exactly from the list above, ordered " +
            "most relevant first, with at most 3 entries. If the question does not relate to any of these " +
            "entities (greetings, small talk, or anything unrelated to this application's data), respond " +
            "with an empty array []. No markdown fences, no commentary.";

    private final AiEntityMetadataService metadataService;
    private final AiDatabaseAuthorizationService authorizationService;
    private final AiChatClient aiChatClient;
    private final AiQueryProperties properties;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public NaturalLanguageQueryTranslator(AiEntityMetadataService metadataService,
                                           AiDatabaseAuthorizationService authorizationService,
                                           AiChatClient aiChatClient,
                                           AiQueryProperties properties) {
        this.metadataService = metadataService;
        this.authorizationService = authorizationService;
        this.aiChatClient = aiChatClient;
        this.properties = properties;
    }

    public TranslatedQueryRequest translate(User user, String naturalLanguageQuery) {
        if (naturalLanguageQuery == null || naturalLanguageQuery.trim().isEmpty()) {
            throw new AiQueryValidationException("A question is required.");
        }
        List<ResolvedEntity> accessibleEntities = listAccessibleEntities(user);
        if (accessibleEntities.isEmpty()) {
            throw new AiQueryValidationException("No queryable data is available for this user.");
        }

        List<ResolvedEntity> candidateEntities = accessibleEntities;
        if (accessibleEntities.size() > properties.getSchemaLinkingEntityThreshold()) {
            candidateEntities = selectRelevantEntities(naturalLanguageQuery, accessibleEntities);
            if (candidateEntities.isEmpty()) {
                LOGGER.warn("Question '{}' did not link to any of the {} accessible entities.",
                        naturalLanguageQuery, accessibleEntities.size());
                throw new AiQueryValidationException("The question could not be matched to any queryable data.");
            }
        }

        String schema = describeSchema(user, candidateEntities);
        if (schema.isEmpty()) {
            throw new AiQueryValidationException("The question could not be matched to any queryable data.");
        }
        String systemPrompt = "You translate a user's natural-language question into a structured, " +
                "read-only database query against the schema below. You never invent entities or " +
                "fields that are not listed. Today's date is " + LocalDate.now() + " (yyyy-MM-dd) -- use it to " +
                "resolve relative date references such as \"this year\", \"this month\", \"today\", or " +
                "\"last 30 days\" into concrete filter values.\n\n" + schema + "\n" + RESPONSE_CONTRACT;

        LOGGER.debug("AI query schema for user {}:\n{}", user == null ? null : user.getUsername(), schema);

        String raw = aiChatClient.generate(systemPrompt, naturalLanguageQuery);
        LOGGER.debug("Model raw response for question '{}': {}", naturalLanguageQuery, raw);
        JsonNode root = parseJson(raw);

        String entityName = textOrNull(root.path("entity"));
        if (entityName == null) {
            LOGGER.warn("Model could not match question '{}' to any entity. Raw response: {}", naturalLanguageQuery, raw);
            throw new AiQueryValidationException("The question could not be matched to any queryable data.");
        }

        return new TranslatedQueryRequest(
                entityName,
                readFieldList(root.path("fields")),
                readFilters(root.path("filters")),
                readFilterGroups(root.path("filterGroups")),
                readSort(root.path("sort")),
                readInteger(root.path("page")),
                readInteger(root.path("pageSize")),
                readAggregation(root.path("aggregation")));
    }

    /** Entities the user can see at all, i.e. accessible with at least one accessible field. */
    private List<ResolvedEntity> listAccessibleEntities(User user) {
        List<ResolvedEntity> accessible = new ArrayList<ResolvedEntity>();
        for (ResolvedEntity entity : metadataService.listEntities()) {
            if (!authorizationService.canAccessEntity(user, entity.getEntityName())) {
                continue;
            }
            boolean hasAccessibleField = false;
            for (ResolvedField field : entity.getAllFields()) {
                if (authorizationService.canAccessField(user, entity.getEntityName(), field.getName())) {
                    hasAccessibleField = true;
                    break;
                }
            }
            if (hasAccessibleField) {
                accessible.add(entity);
            }
        }
        return accessible;
    }

    /**
     * Asks the model a cheap, entity-names-only question to narrow down which of the caller's
     * accessible entities the natural-language question is actually about, so the follow-up call
     * only has to carry full field detail for those entities rather than the whole schema. Used
     * once the accessible entity count exceeds {@link AiQueryProperties#getSchemaLinkingEntityThreshold()}.
     */
    private List<ResolvedEntity> selectRelevantEntities(String naturalLanguageQuery, List<ResolvedEntity> accessibleEntities) {
        String entityNames = describeEntityNames(accessibleEntities);
        String systemPrompt = "You route a user's natural-language question to the data entities it is " +
                "asking about.\n\nAvailable entities:\n" + entityNames + "\n" + ENTITY_LINKING_INSTRUCTIONS;

        String raw = aiChatClient.generate(systemPrompt, naturalLanguageQuery);
        LOGGER.debug("Entity-linking response for question '{}': {}", naturalLanguageQuery, raw);

        List<String> selectedNames;
        try {
            JsonNode root = parseJson(raw);
            selectedNames = readFieldList(root);
        } catch (Exception ex) {
            LOGGER.warn("Could not parse entity-linking response for question '{}': {}", naturalLanguageQuery, raw);
            return Collections.emptyList();
        }
        List<ResolvedEntity> matched = filterByAuthorizedNames(accessibleEntities, selectedNames);
        return withHistoryVariants(accessibleEntities, matched);
    }

    private static final String[] HISTORY_ENTITY_SUFFIXES = {"Log", "History", "Audit", "Trail"};

    /**
     * Entity-linking is a single, cheap LLM call asked to guess up to 3 relevant entity names
     * from bare names alone (no field detail) -- it reliably conflates a "current state" entity
     * (e.g. {@code ProjectRequisitionWorkFlow}) with its own history/detail table (e.g. {@code
     * ProjectRequisitionWorkFlowLog}), since the latter is exactly what a "how long"/"which
     * stage"/timeline-style question actually needs but the model has no way to know that from
     * a name list alone. Deterministically (no extra LLM call) adds any accessible entity whose
     * simple name is one of the matched entities' simple names plus a common
     * history/audit-table suffix, so its fields (and duration-aggregation candidates) are always
     * offered alongside the entity the model actually asked for.
     */
    private List<ResolvedEntity> withHistoryVariants(List<ResolvedEntity> accessibleEntities, List<ResolvedEntity> matched) {
        List<ResolvedEntity> expanded = new ArrayList<ResolvedEntity>(matched);
        for (ResolvedEntity entity : matched) {
            String simpleName = simpleNameOf(entity.getEntityName());
            for (String suffix : HISTORY_ENTITY_SUFFIXES) {
                String variantName = simpleName + suffix;
                for (ResolvedEntity candidate : accessibleEntities) {
                    if (!expanded.contains(candidate) && simpleNameOf(candidate.getEntityName()).equals(variantName)) {
                        expanded.add(candidate);
                    }
                }
            }
        }
        return expanded;
    }

    private String simpleNameOf(String qualifiedEntityName) {
        int lastDot = qualifiedEntityName.lastIndexOf('.');
        return lastDot < 0 ? qualifiedEntityName : qualifiedEntityName.substring(lastDot + 1);
    }

    private String describeEntityNames(List<ResolvedEntity> entities) {
        StringBuilder sb = new StringBuilder();
        for (ResolvedEntity entity : entities) {
            sb.append("- ").append(entity.getEntityName()).append("\n");
        }
        return sb.toString();
    }

    /** Keeps only model-selected names that actually match an entity the caller is authorized to see. */
    private List<ResolvedEntity> filterByAuthorizedNames(List<ResolvedEntity> accessibleEntities, List<String> selectedNames) {
        List<ResolvedEntity> matched = new ArrayList<ResolvedEntity>();
        if (selectedNames == null) {
            return matched;
        }
        for (String name : selectedNames) {
            for (ResolvedEntity entity : accessibleEntities) {
                if (entity.getEntityName().equalsIgnoreCase(name) && !matched.contains(entity)) {
                    matched.add(entity);
                    break;
                }
            }
        }
        return matched;
    }

    private String describeSchema(User user, List<ResolvedEntity> entities) {
        StringBuilder schema = new StringBuilder();
        for (ResolvedEntity entity : entities) {
            StringBuilder fieldsLine = new StringBuilder();
            appendFields(user, entity, entity.getAllFields(), null, fieldsLine);
            for (ResolvedRelation relation : entity.getAllRelations()) {
                if (!authorizationService.canAccessField(user, entity.getEntityName(), relation.getName())) {
                    continue;
                }
                ResolvedEntity targetEntity = relation.getTargetEntity();
                appendFields(user, targetEntity, targetEntity.getAllFields(), relation.getName(), fieldsLine);
            }
            if (fieldsLine.length() == 0) {
                continue;
            }
            schema.append("Entity ").append(entity.getEntityName()).append(": ").append(fieldsLine).append("\n");
        }
        return schema.toString();
    }

    /** Appends "name:Type" (or "prefix.name:Type") entries for every field the user can access. */
    private void appendFields(User user, ResolvedEntity fieldOwner, Collection<ResolvedField> fields, String prefix,
                               StringBuilder fieldsLine) {
        for (ResolvedField field : fields) {
            if (!authorizationService.canAccessField(user, fieldOwner.getEntityName(), field.getName())) {
                continue;
            }
            if (fieldsLine.length() > 0) {
                fieldsLine.append(", ");
            }
            String qualifiedName = prefix == null ? field.getName() : prefix + "." + field.getName();
            fieldsLine.append(qualifiedName).append(":").append(field.getJavaType().getSimpleName());
            if (!field.getAllowedValues().isEmpty()) {
                fieldsLine.append("[").append(joinWithPipe(field.getAllowedValues())).append("]");
            }
        }
    }

    private String joinWithPipe(List<String> values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append("|");
            }
            sb.append(values.get(i));
        }
        return sb.toString();
    }

    private JsonNode parseJson(String raw) {
        String cleaned = stripMarkdownFence(raw);
        try {
            return mapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new AiQueryValidationException("The question could not be understood; please rephrase it.");
        }
    }

    private String stripMarkdownFence(String raw) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            int lastFence = text.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                text = text.substring(firstNewline + 1, lastFence).trim();
            }
        }
        return text;
    }

    private List<String> readFieldList(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        List<String> fields = new ArrayList<String>();
        for (JsonNode item : node) {
            fields.add(item.asText());
        }
        return fields;
    }

    private List<FilterCriterion> readFilters(JsonNode node) {
        List<FilterCriterion> filters = new ArrayList<FilterCriterion>();
        if (!node.isArray()) {
            return filters;
        }
        for (JsonNode item : node) {
            String field = textOrNull(item.path("field"));
            FilterOperator operator = enumOrNull(FilterOperator.class, item.path("operator"));
            if (field == null || operator == null) {
                continue;
            }
            filters.add(new FilterCriterion(field, operator, readValue(item.path("value"))));
        }
        return filters;
    }

    private List<FilterGroup> readFilterGroups(JsonNode node) {
        List<FilterGroup> groups = new ArrayList<FilterGroup>();
        if (!node.isArray()) {
            return groups;
        }
        for (JsonNode groupNode : node) {
            List<FilterCriterion> anyOf = readFilters(groupNode);
            if (!anyOf.isEmpty()) {
                groups.add(new FilterGroup(anyOf));
            }
        }
        return groups;
    }

    private List<SortCriterion> readSort(JsonNode node) {
        List<SortCriterion> sorts = new ArrayList<SortCriterion>();
        if (!node.isArray()) {
            return sorts;
        }
        for (JsonNode item : node) {
            String field = textOrNull(item.path("field"));
            SortDirection direction = enumOrNull(SortDirection.class, item.path("direction"));
            if (field == null || direction == null) {
                continue;
            }
            sorts.add(new SortCriterion(field, direction));
        }
        return sorts;
    }

    private AggregationRequest readAggregation(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        AggregationFunction function = enumOrNull(AggregationFunction.class, node.path("function"));
        if (function == null) {
            return null;
        }
        String field = textOrNull(node.path("field"));
        List<String> groupBy = readFieldList(node.path("groupBy"));
        String asPercentageOf = textOrNull(node.path("asPercentageOf"));
        String subtractField = textOrNull(node.path("subtractField"));
        String durationStartField = textOrNull(node.path("durationStartField"));
        String durationEndField = textOrNull(node.path("durationEndField"));
        String durationUnit = textOrNull(node.path("durationUnit"));
        return new AggregationRequest(function, field, groupBy, asPercentageOf, subtractField,
                durationStartField, durationEndField, durationUnit);
    }

    private Object readValue(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isArray()) {
            List<Object> values = new ArrayList<Object>();
            for (JsonNode item : node) {
                values.add(scalar(item));
            }
            return values;
        }
        return scalar(node);
    }

    private Object scalar(JsonNode node) {
        if (node.isNumber()) {
            return node.numberValue();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        return node.asText();
    }

    private Integer readInteger(JsonNode node) {
        return node.isInt() ? node.intValue() : null;
    }

    private String textOrNull(JsonNode node) {
        return (node == null || node.isMissingNode() || node.isNull()) ? null : node.asText();
    }

    private <E extends Enum<E>> E enumOrNull(Class<E> type, JsonNode node) {
        String text = textOrNull(node);
        if (text == null) {
            return null;
        }
        try {
            return Enum.valueOf(type, text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
