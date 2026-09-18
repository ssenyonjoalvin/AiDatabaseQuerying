package org.pahappa.systems.aiquery.typeconversion;

import org.pahappa.systems.aiquery.dto.FilterOperator;
import org.pahappa.systems.aiquery.exception.AiQueryValidationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.Temporal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * The single place that knows how to (a) decide which {@link FilterOperator}s make sense
 * for a given persistent attribute's Java type, and (b) safely convert model-supplied
 * scalar values into that type. Never evaluates or executes anything supplied by the
 * model -- it only parses plain scalars (numbers, strings, booleans) into typed Java
 * objects that are later used exclusively as JPA Criteria bind values.
 */
@Service
public class AiTypeConversionService {

    private static final List<FilterOperator> STRING_OPERATORS = Collections.unmodifiableList(Arrays.asList(
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS, FilterOperator.LIKE,
            FilterOperator.STARTS_WITH, FilterOperator.ENDS_WITH,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL, FilterOperator.IN));

    private static final List<FilterOperator> ORDERED_OPERATORS = Collections.unmodifiableList(Arrays.asList(
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.GREATER_THAN, FilterOperator.GREATER_THAN_OR_EQUAL,
            FilterOperator.LESS_THAN, FilterOperator.LESS_THAN_OR_EQUAL,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL, FilterOperator.IN));

    private static final List<FilterOperator> BOOLEAN_OPERATORS = Collections.unmodifiableList(Arrays.asList(
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL));

    private static final List<FilterOperator> ENUM_OPERATORS = Collections.unmodifiableList(Arrays.asList(
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL, FilterOperator.IN));

    private static final List<FilterOperator> FALLBACK_OPERATORS = Collections.unmodifiableList(Arrays.asList(
            FilterOperator.EQUALS, FilterOperator.NOT_EQUALS,
            FilterOperator.IS_NULL, FilterOperator.IS_NOT_NULL));

    public static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    public List<FilterOperator> supportedOperatorsFor(Class<?> javaType) {
        Class<?> type = box(javaType);
        if (type == String.class) {
            return STRING_OPERATORS;
        }
        if (Number.class.isAssignableFrom(type)) {
            return ORDERED_OPERATORS;
        }
        if (type == Boolean.class) {
            return BOOLEAN_OPERATORS;
        }
        if (Temporal.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type)) {
            return ORDERED_OPERATORS;
        }
        if (type.isEnum()) {
            return ENUM_OPERATORS;
        }
        return FALLBACK_OPERATORS;
    }

    public boolean isOperatorSupported(Class<?> javaType, FilterOperator operator) {
        return supportedOperatorsFor(javaType).contains(operator);
    }

    public boolean isNumeric(Class<?> javaType) {
        return Number.class.isAssignableFrom(box(javaType));
    }

    /** True for a {@code java.time} temporal type or a legacy {@code java.util.Date}/subclass. */
    public boolean isTemporal(Class<?> javaType) {
        Class<?> type = box(javaType);
        return Temporal.class.isAssignableFrom(type) || Date.class.isAssignableFrom(type);
    }

    /**
     * Converts a single scalar value into {@code targetType}. Only ever produces a plain
     * typed Java object (never SQL/JPQL text); on any parse failure it throws a generic,
     * safe {@link AiQueryValidationException} without echoing the underlying parser error.
     */
    public Object convertSingle(String fieldName, Class<?> targetType, Object rawValue) {
        Class<?> type = box(targetType);
        if (rawValue == null) {
            throw new AiQueryValidationException("Value for field '" + fieldName + "' must not be null.");
        }
        try {
            if (type == String.class) {
                return rawValue.toString();
            }
            if (type.isInstance(rawValue)) {
                return rawValue;
            }
            String text = rawValue.toString().trim();
            if (type == Integer.class) return Integer.valueOf(text);
            if (type == Long.class) return Long.valueOf(text);
            if (type == Double.class) return Double.valueOf(text);
            if (type == Float.class) return Float.valueOf(text);
            if (type == Short.class) return Short.valueOf(text);
            if (type == Byte.class) return Byte.valueOf(text);
            if (type == BigDecimal.class) return new BigDecimal(text);
            if (type == BigInteger.class) return new BigInteger(text);
            if (type == Boolean.class) {
                if (text.equalsIgnoreCase("true")) return Boolean.TRUE;
                if (text.equalsIgnoreCase("false")) return Boolean.FALSE;
                throw new IllegalArgumentException("not a boolean");
            }
            if (type == LocalDate.class) return LocalDate.parse(text);
            if (type == LocalDateTime.class) return LocalDateTime.parse(text);
            if (type == LocalTime.class) return LocalTime.parse(text);
            if (Date.class.isAssignableFrom(type)) return parseLegacyDate(text);
            if (type.isEnum()) {
                return matchEnumConstant(type, text, fieldName);
            }
            throw new AiQueryValidationException("Field '" + fieldName + "' does not support filtering by value.");
        } catch (AiQueryValidationException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new AiQueryValidationException("Invalid value for field '" + fieldName + "'.");
        }
    }

    /**
     * Parses an ISO-8601 date or date-time string into a legacy {@link Date}, for entities
     * (common across older JPA models) that still use {@code java.util.Date} instead of
     * {@code java.time} types. Interpreted in the server's default time zone; a date-only value
     * (e.g. "2026-01-01") is taken as midnight that day.
     */
    private Date parseLegacyDate(String text) {
        try {
            return Date.from(LocalDate.parse(text).atStartOfDay(ZoneId.systemDefault()).toInstant());
        } catch (RuntimeException ex) {
            return Date.from(LocalDateTime.parse(text).atZone(ZoneId.systemDefault()).toInstant());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private Object matchEnumConstant(Class<?> enumType, String text, String fieldName) {
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum) constant).name().equalsIgnoreCase(text)) {
                return constant;
            }
        }
        throw new AiQueryValidationException("Invalid value for field '" + fieldName + "'.");
    }

    /**
     * Converts a filter value according to its operator: {@code null} for the two
     * no-value operators, a converted {@link List} for {@link FilterOperator#IN}, or a
     * single converted scalar otherwise.
     */
    public Object convertForOperator(String fieldName, Class<?> targetType, FilterOperator operator, Object rawValue) {
        if (operator == FilterOperator.IS_NULL || operator == FilterOperator.IS_NOT_NULL) {
            return null;
        }
        if (operator == FilterOperator.IN) {
            if (!(rawValue instanceof List<?>) || ((List<?>) rawValue).isEmpty()) {
                throw new AiQueryValidationException("Operator IN requires a non-empty list of values for field '" + fieldName + "'.");
            }
            List<?> list = (List<?>) rawValue;
            List<Object> converted = new ArrayList<Object>();
            for (Object v : list) {
                converted.add(convertSingle(fieldName, targetType, v));
            }
            return converted;
        }
        if (rawValue instanceof List) {
            throw new AiQueryValidationException("Operator " + operator + " does not accept multiple values for field '" + fieldName + "'.");
        }
        if (rawValue == null) {
            throw new AiQueryValidationException("A value is required for operator " + operator + " on field '" + fieldName + "'.");
        }
        return convertSingle(fieldName, targetType, rawValue);
    }

    public List<String> enumConstantNames(Class<?> javaType) {
        Class<?> type = box(javaType);
        if (!type.isEnum()) {
            return Collections.emptyList();
        }
        Object[] constants = type.getEnumConstants();
        List<String> names = new ArrayList<String>();
        for (Object c : constants) {
            names.add(((Enum<?>) c).name());
        }
        return names;
    }

    public String displayName(Class<?> javaType) {
        return box(javaType).getSimpleName();
    }
}
