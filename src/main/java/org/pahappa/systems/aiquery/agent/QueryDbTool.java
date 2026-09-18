package org.pahappa.systems.aiquery.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.pahappa.systems.aiquery.client.ToolDefinition;
import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.DurationUnit;
import org.pahappa.systems.aiquery.dto.FilterOperator;
import org.pahappa.systems.aiquery.dto.QueryResult;
import org.pahappa.systems.aiquery.dto.SortDirection;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;
import org.pahappa.systems.aiquery.exception.AiQueryAuthorizationException;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.pahappa.systems.aiquery.service.AiQueryService;
import org.pahappa.systems.aiquery.service.TranslatedQueryRequestParser;
import org.sers.webutils.model.security.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * The one tool the agentic loop ({@code AgenticQueryOrchestrator}) offers the model: run one
 * query plan and get rows back. Every call is parsed into the same {@link TranslatedQueryRequest}
 * shape {@link TranslatedQueryRequestParser} already produces for the single-shot path, then
 * executed through {@link AiQueryService#queryEntity} -- the exact same validate (authorization,
 * {@code @AiNotQueryable}, operator/type checks, limits) -> build -> audit-log path a
 * directly-submitted query goes through. There is no shortcut here: the validator runs on every
 * single call, not just the first.
 */
@Service
public class QueryDbTool {

    public static final String NAME = "query_db";

    private static final String DESCRIPTION =
            "Runs one read-only database query and returns the matching rows. Only entity and " +
            "field names already listed in the schema above may be used -- never invent one. " +
            "Call this as many times as needed (each call is independently re-validated) before " +
            "giving your final answer.";

    private final AiQueryService aiQueryService;
    private final TranslatedQueryRequestParser requestParser;
    private final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public QueryDbTool(AiQueryService aiQueryService, TranslatedQueryRequestParser requestParser) {
        this.aiQueryService = aiQueryService;
        this.requestParser = requestParser;
    }

    public ToolDefinition definition() {
        return new ToolDefinition(NAME, DESCRIPTION, parametersSchema());
    }

    public ToolExecutionResult execute(User user, String argumentsJson) {
        TranslatedQueryRequest plan;
        try {
            plan = requestParser.parseRequest(argumentsJson);
        } catch (AiQueryValidationException ex) {
            return ToolExecutionResult.failure(null, ex.getMessage());
        }

        try {
            QueryResult result = aiQueryService.queryEntity(user, plan.entityName(), plan.fields(), plan.filters(),
                    plan.filterGroups(), plan.sort(), plan.page(), plan.pageSize(), plan.aggregation());
            return ToolExecutionResult.success(plan, result, describeResult(result));
        } catch (AiQueryAuthorizationException ex) {
            // Deliberately generic: never echo back which entity/field access was denied for.
            return ToolExecutionResult.failure(plan, "Access to that data was denied.");
        } catch (AiQueryValidationException ex) {
            return ToolExecutionResult.failure(plan, ex.getMessage());
        }
    }

    private String describeResult(QueryResult result) {
        try {
            StringBuilder message = new StringBuilder();
            message.append("entity=").append(result.entity())
                    .append(", fields=").append(result.fields())
                    .append(", returnedCount=").append(result.returnedCount());
            if (result.hasMore()) {
                message.append(" (more rows exist beyond this page)");
            }
            message.append("\nrows (JSON array):\n").append(mapper.writeValueAsString(result.rows()));
            return message.toString();
        } catch (Exception ex) {
            return "Query succeeded but the result could not be serialized.";
        }
    }

    /**
     * Built directly from {@link TranslatedQueryRequest}'s real shape and the operator/function/
     * direction/unit enums {@code AiQueryValidator} actually accepts -- not hand-written prose --
     * so the model never has a schema advertising something the validator will reject.
     */
    private ObjectNode parametersSchema() {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");
        ObjectNode properties = schema.putObject("properties");

        properties.putObject("entity")
                .put("type", "string")
                .put("description", "Exact entity name, copied from the schema above.");

        ObjectNode fields = properties.putObject("fields");
        fields.put("type", "array");
        fields.putObject("items").put("type", "string");
        fields.put("description", "Field names to return. Omit or leave empty for the entity's default fields.");

        properties.set("filters", filterArraySchema("Conditions AND'd together."));

        ObjectNode filterGroups = properties.putObject("filterGroups");
        filterGroups.put("type", "array");
        filterGroups.set("items", filterArraySchema(null));
        filterGroups.put("description",
                "Each inner array is OR'd internally; groups and 'filters' are AND'd together. " +
                "Use for an 'or' between two conditions.");

        ObjectNode sort = properties.putObject("sort");
        sort.put("type", "array");
        ObjectNode sortItem = sort.putObject("items");
        sortItem.put("type", "object");
        ObjectNode sortItemProps = sortItem.putObject("properties");
        sortItemProps.putObject("field").put("type", "string");
        putEnum(sortItemProps.putObject("direction"), SortDirection.values());
        sortItem.set("required", stringArray("field", "direction"));

        properties.putObject("page").put("type", "integer").put("description", "Zero-based page number. Omit for page 0.");
        properties.putObject("pageSize").put("type", "integer").put("description", "Rows per page.");

        properties.set("aggregation", aggregationSchema());

        schema.set("required", stringArray("entity"));
        return schema;
    }

    private ObjectNode filterArraySchema(String description) {
        ObjectNode array = mapper.createObjectNode();
        array.put("type", "array");
        ObjectNode item = array.putObject("items");
        item.put("type", "object");
        ObjectNode itemProps = item.putObject("properties");
        itemProps.putObject("field").put("type", "string");
        putEnum(itemProps.putObject("operator"), FilterOperator.values());
        itemProps.putObject("value").put("description",
                "A scalar (string/number/boolean), a list only for operator IN, or omitted for IS_NULL/IS_NOT_NULL.");
        item.set("required", stringArray("field", "operator"));
        if (description != null) {
            array.put("description", description);
        }
        return array;
    }

    private ObjectNode aggregationSchema() {
        ObjectNode aggregation = mapper.createObjectNode();
        aggregation.put("type", "object");
        ObjectNode properties = aggregation.putObject("properties");
        putEnum(properties.putObject("function"), AggregationFunction.values());
        properties.putObject("field").put("type", "string");
        ObjectNode groupBy = properties.putObject("groupBy");
        groupBy.put("type", "array");
        groupBy.putObject("items").put("type", "string");
        properties.putObject("asPercentageOf").put("type", "string");
        properties.putObject("subtractField").put("type", "string");
        properties.putObject("durationStartField").put("type", "string");
        properties.putObject("durationEndField").put("type", "string");
        putEnum(properties.putObject("durationUnit"), DurationUnit.values());
        aggregation.put("description", "Omit entirely for a plain (non-aggregated) query.");
        return aggregation;
    }

    private void putEnum(ObjectNode node, Enum<?>[] values) {
        node.put("type", "string");
        ArrayNode enumNode = node.putArray("enum");
        for (Enum<?> value : values) {
            enumNode.add(value.name());
        }
    }

    private ArrayNode stringArray(String... values) {
        ArrayNode array = mapper.createArrayNode();
        for (String value : values) {
            array.add(value);
        }
        return array;
    }
}
