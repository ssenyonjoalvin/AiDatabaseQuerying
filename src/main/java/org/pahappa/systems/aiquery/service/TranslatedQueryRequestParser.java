package org.pahappa.systems.aiquery.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.pahappa.systems.aiquery.dto.AggregationFunction;
import org.pahappa.systems.aiquery.dto.AggregationRequest;
import org.pahappa.systems.aiquery.dto.FilterCriterion;
import org.pahappa.systems.aiquery.dto.FilterGroup;
import org.pahappa.systems.aiquery.dto.FilterOperator;
import org.pahappa.systems.aiquery.dto.SortCriterion;
import org.pahappa.systems.aiquery.dto.SortDirection;
import org.pahappa.systems.aiquery.dto.TranslatedQueryRequest;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Parses the one JSON shape a model is ever asked to produce a query plan in -- whether as the
 * single free-text response {@link NaturalLanguageQueryTranslator} expects, or as a tool call's
 * arguments in the agentic loop -- into a {@link TranslatedQueryRequest}. Purely structural: it
 * never resolves an entity/field name or checks authorization, so a caller must still run the
 * result through {@link org.pahappa.systems.aiquery.validation.AiQueryValidator} before it can
 * reach the database.
 */
@Service
public class TranslatedQueryRequestParser {

    private final ObjectMapper mapper = new ObjectMapper();

    /** Strips an optional markdown code fence and parses the remainder as JSON. */
    public JsonNode parseJson(String raw) {
        String cleaned = stripMarkdownFence(raw);
        try {
            return mapper.readTree(cleaned);
        } catch (Exception ex) {
            throw new AiQueryValidationException("The question could not be understood; please rephrase it.");
        }
    }

    public TranslatedQueryRequest parseRequest(String raw) {
        return parseRequest(parseJson(raw));
    }

    public TranslatedQueryRequest parseRequest(JsonNode root) {
        return new TranslatedQueryRequest(
                textOrNull(root.path("entity")),
                readStringList(root.path("fields")),
                readFilters(root.path("filters")),
                readFilterGroups(root.path("filterGroups")),
                readSort(root.path("sort")),
                readInteger(root.path("page")),
                readInteger(root.path("pageSize")),
                readAggregation(root.path("aggregation")));
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

    public List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return null;
        }
        List<String> values = new ArrayList<String>();
        for (JsonNode item : node) {
            values.add(item.asText());
        }
        return values;
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
        List<String> groupBy = readStringList(node.path("groupBy"));
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

    public String textOrNull(JsonNode node) {
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
