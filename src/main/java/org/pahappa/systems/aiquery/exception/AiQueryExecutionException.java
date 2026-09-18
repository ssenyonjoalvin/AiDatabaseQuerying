package org.pahappa.systems.aiquery.exception;

/**
 * Thrown after a query passed validation but failed during execution. The message is
 * always a generic, pre-composed string -- the real cause is logged internally and never
 * propagated here, so no Hibernate/JDBC/SQL detail ever reaches the model.
 */
public class AiQueryExecutionException extends RuntimeException {

    public AiQueryExecutionException(String message) {
        super(message);
    }
}
