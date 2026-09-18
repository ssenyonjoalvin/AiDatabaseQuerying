package org.pahappa.systems.aiquery.web;

import org.codehaus.jackson.annotate.JsonAutoDetect;
import org.codehaus.jackson.annotate.JsonMethod;
import org.codehaus.jackson.map.ObjectMapper;

/**
 * ai-query-engine's DTOs (e.g. QueryResult, FilterCriterion) expose their private fields
 * through fluent accessors (entity(), field()) rather than JavaBean getters (getEntity()),
 * so Jackson 1.x's default getter-based introspection finds no properties and refuses to
 * serialize them. Reading fields directly instead sidesteps that.
 *
 * <p>This is an optional compatibility shim for hosts still wiring their MVC
 * {@code HttpMessageConverter}s against Jackson 1.x ({@code org.codehaus.jackson}), e.g. via
 * Spring's {@code MappingJacksonHttpMessageConverter}. Hosts on Jackson 2.x can serialize
 * {@code AiController}'s responses with their existing converter and don't need this class.
 */
public final class AiQueryObjectMapperFactory {

    private AiQueryObjectMapperFactory() {
    }

    public static ObjectMapper create() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setVisibility(JsonMethod.FIELD, JsonAutoDetect.Visibility.ANY);
        return mapper;
    }
}
