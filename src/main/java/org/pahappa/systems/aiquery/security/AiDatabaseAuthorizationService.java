package org.pahappa.systems.aiquery.security;

import org.sers.webutils.model.security.User;

/**
 * The application-owned security boundary for AI-driven queries. The caller never decides
 * what it can see -- every entity and field access is independently checked here, against
 * the authenticated user, regardless of what was requested or how it was phrased.
 */
public interface AiDatabaseAuthorizationService {

    boolean canAccessEntity(User user, String entityName);

    boolean canAccessField(User user, String entityName, String fieldName);
}
