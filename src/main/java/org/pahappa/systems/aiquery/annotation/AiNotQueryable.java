package org.pahappa.systems.aiquery.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a persistent JPA attribute as never queryable by the AI, regardless of the
 * authenticated user or requested operation. All other persistent attributes are
 * queryable by default -- this is the only annotation the query system understands.
 * Can be placed on the field or its accessor; both are checked regardless of the
 * entity's JPA access strategy.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface AiNotQueryable {
}
