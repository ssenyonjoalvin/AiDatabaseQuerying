package org.pahappa.systems.aiquery.security;

import org.sers.webutils.model.security.User;

/**
 * Placeholder implementation the host application can wire explicitly (e.g. as a
 * {@code <bean>} in its Spring XML config) when it has not yet integrated real
 * authentication. Reports a fixed pseudo-user rather than blocking every query outright.
 * Replace with a bean backed by real authentication before relying on per-user
 * authorization or audit trails in production. Not self-registering -- this module has no
 * Spring Boot autoconfiguration, so the host must declare exactly one
 * {@link AiCurrentUserProvider} bean itself.
 */
public class DefaultAiCurrentUserProvider implements AiCurrentUserProvider {

    @Override
    public User getCurrentUser() {
        User user = new User();
        user.setUsername("demo-user");
        return user;
    }
}
