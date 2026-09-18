package org.pahappa.systems.aiquery.config;

import org.sers.webutils.server.core.utils.PropertyPlaceHolderConfigurer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Resolves the {@code ${ai.*}} placeholders consumed by
 * {@link org.pahappa.systems.aiquery.client.AiChatClient}. Component-scanning this package (see
 * {@link AiQueryProperties}) picks this class up automatically.
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
@Configuration
public class AiQueryModelConfig {

    @Bean
    public static PropertyPlaceHolderConfigurer aiPropertyPlaceholderConfigurer() {
        PropertyPlaceHolderConfigurer configurer = new PropertyPlaceHolderConfigurer();
        configurer.setClassPathPropertiesFilename("ai.local.properties");
        configurer.setEnvironmentVariable("AI_PROPERTIES_FILE");
        configurer.setIgnoreResourceNotFound(true);
        configurer.setIgnoreUnresolvablePlaceholders(true);
        return configurer;
    }
}
