package org.pahappa.systems.aiquery.security;

import org.sers.webutils.model.security.User;

/**
 * Resolves who is currently asking, so authorization and audit logging always have a
 * real identity to check/record. Applications with an authentication mechanism should
 * supply their own bean (backed by e.g. Spring Security's SecurityContext) instead of
 * relying on the placeholder default.
 */
public interface AiCurrentUserProvider {

    User getCurrentUser();
}
