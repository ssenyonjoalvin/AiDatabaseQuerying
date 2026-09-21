package org.pahappa.systems.aiquery.config;

import org.sers.webutils.server.core.utils.PropertyPlaceHolderConfigurer;
import org.springframework.core.PriorityOrdered;
import org.springframework.stereotype.Component;

/**
 * Resolves the {@code ${ai.*}} placeholders consumed by
 * {@link org.pahappa.systems.aiquery.client.AiChatClient}. Component-scanning this package (see
 * {@link AiQueryProperties}) picks this class up automatically.
 *
 * <p>Registered as a {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}
 * (via {@link PropertyPlaceHolderConfigurer}) so placeholders are applied before {@code @Value}
 * injection on AI beans. A {@code @Configuration} {@code @Bean} factory is too late when the host
 * application discovers components through webutils' dynamic classpath scan.
 *
 * <p>Two sources are checked, so the same key/secret doesn't need to be duplicated into every
 * host application's own classpath:
 * <ul>
 *   <li>The {@code AI_PROPERTIES_FILE} environment variable (or, failing that, system property),
 *       treated as an absolute path to a properties file. This lets every host app on a given
 *       server point at the *same* external file -- set the variable once (e.g. in Tomcat's
 *       {@code setenv.sh}) instead of shipping {@code ai.local.properties} inside each WAR.</li>
 *   <li>An {@code ai.local.properties} file on the host application's runtime classpath, as a
 *       convenient per-developer/local-testing fallback when the environment variable isn't set.</li>
 * </ul>
 * Neither source is required -- if both are absent, {@code ai.api-key} resolves to an empty
 * string (see the {@code :} default on {@code AiChatClient}'s {@code @Value}), and
 * {@code AiChatClient} fails fast with a clear error only when it is actually called. Hosts that
 * already supply {@code ai.api-key} some other way (their own placeholder configurer, environment,
 * etc.) are unaffected either way.
 */
@Component
public class AiQueryModelConfig extends PropertyPlaceHolderConfigurer implements PriorityOrdered {

    public AiQueryModelConfig() {
        setClassPathPropertiesFilename("ai.local.properties");
        setEnvironmentVariable("AI_PROPERTIES_FILE");
        setIgnoreResourceNotFound(true);
        setIgnoreUnresolvablePlaceholders(true);
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }
}
