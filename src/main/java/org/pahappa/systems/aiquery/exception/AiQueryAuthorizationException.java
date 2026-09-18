package org.pahappa.systems.aiquery.exception;

/**
 * Thrown when the authenticated user is not permitted to access a requested entity or
 * field. Kept distinct from {@link AiQueryValidationException} so callers/tests can tell
 * "malformed request" apart from "authorization denied".
 */
public class AiQueryAuthorizationException extends AiQueryValidationException {

    public AiQueryAuthorizationException(String message) {
        super(message);
    }
}
