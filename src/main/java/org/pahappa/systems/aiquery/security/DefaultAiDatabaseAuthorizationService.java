package org.pahappa.systems.aiquery.security;

import org.sers.webutils.model.security.User;

/**
 * Placeholder implementation the host application can wire explicitly (e.g. as a
 * {@code <bean>} in its Spring XML config) when it has no per-entity/per-field ACL model
 * yet. Grants access to any authenticated user and denies everyone else. Replace with real
 * role/ownership checks before relying on it in production. Not self-registering -- this
 * module has no Spring Boot autoconfiguration, so the host must declare exactly one
 * {@link AiDatabaseAuthorizationService} bean itself.
 */
public class DefaultAiDatabaseAuthorizationService implements AiDatabaseAuthorizationService {

    @Override
    public boolean canAccessEntity(User user, String entityName) {
        return user != null ;
    }

    @Override
    public boolean canAccessField(User user, String entityName, String fieldName) {
        return canAccessEntity(user, entityName);
    }
}
