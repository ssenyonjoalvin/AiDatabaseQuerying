package org.pahappa.systems.aiquery.exception;

/**
 * A controlled, safe-to-show-to-the-model validation failure. Messages on this exception
 * are always hand-composed by the query system itself -- never a wrapped Hibernate/JDBC
 * message -- so it is always safe to return {@link #getMessage()} to the LLM.
 */
public class AiQueryValidationException extends RuntimeException {

    public AiQueryValidationException(String message) {
        super(message);
    }
}
