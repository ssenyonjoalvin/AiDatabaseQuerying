# ai-query-engine

A generic, reflection-driven, read-only query engine for JPA entities: it discovers every
mapped entity and basic-typed attribute from the JPA metamodel, validates structured
query requests against it (authorization, `@AiNotQueryable`, operator/type compatibility,
pagination and filter/field/sort limits), executes them exclusively through the JPA
Criteria API (never string-built SQL/JPQL), and writes a structured audit trail.

The original source (`demo`) does this with a Spring-AI-based tool-calling wrapper and a
`ChatClient`, which require Spring 6/Spring Boot 3+/Java 17 (Spring AI's minimum baseline).
This module reimplements the same natural-language capability without any of that: a plain
HTTP client (`AiChatClient`, `fluent-hc` + Jackson 2.x) talks directly to any OpenAI-compatible
chat/completions API -- Alibaba DashScope's Qwen, Google Gemini, or any other OpenAI-compatible
provider, selected purely via config, no code change needed -- and `NaturalLanguageQueryTranslator`
turns a question into a structured query using only the entities/fields the current user is
authorized to see -- the model never sees or touches anything else. This module is Java 8 /
plain Spring 3.1+ / `javax.persistence` (JPA 2.0/2.1) compatible, specifically so it can be
added to older, non-Boot Spring applications such as `kpi-tracker-services`.

## Build and install

```
cd ai-query-engine
mvn install
```

This installs `org.pahappa.systems.aiquery:ai-query-engine:1.0-SNAPSHOT` into your local `~/.m2`
repository (or push it to your team's shared/internal repository if you use one).

## Consume it from another project

Add one dependency:

```xml
<dependency>
    <groupId>org.pahappa.systems</groupId>
    <artifactId>ai-query-engine</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

The jar declares its Spring/JPA/`javax.annotation`/`webutils` dependencies as `provided` --
it relies on the host application's own Spring, Hibernate/JPA, `javax.annotation-api`, and
`org.sers.webutils:webutils` versions rather than bundling/forcing its own.
`kpi-tracker-services`/`kpi-tracker-models` already provide compatible versions of all of
these. `webutils` in particular matters here: user identity throughout this module (every
`AiQueryService` method, `AiCurrentUserProvider`, `AiDatabaseAuthorizationService`) is
`org.sers.webutils.model.security.User`, the same shared user entity your other systems use --
not a bespoke type of this module's own.

## Wiring required in the host application

This module has **no Spring Boot autoconfiguration** -- it is plain `@Component`/`@Service`
classes, so it works with classic Spring XML or JavaConfig.

### 1. Component scan

Make sure your `<context:component-scan>` (or equivalent JavaConfig) covers the **Java**
package `org.pahappa.systems.aiquery`:

```xml
<context:component-scan base-package="org.pahappa.systems.kpiTracker, org.pahappa.systems.aiquery"/>
```

### 2. One bean the host must supply

`AiQueryService`/`AiQueryValidator` require exactly one bean of:

- `org.pahappa.systems.aiquery.security.AiDatabaseAuthorizationService` -- decides what the
  current user can see. Every entity/field access is checked against it, regardless of what
  was requested or how it was phrased.

A placeholder implementation is included (`DefaultAiDatabaseAuthorizationService`, grants any
authenticated user access to everything) but is **not** self-registering, since there is no
`@ConditionalOnMissingBean`-style mechanism available outside Spring Boot:

```xml
<bean id="aiDatabaseAuthorizationService"
      class="org.pahappa.systems.aiquery.security.DefaultAiDatabaseAuthorizationService"/>
```

Replace it with a real implementation backed by your actual authorization model before
relying on per-user access control or audit trails in production.

`AiCurrentUserProvider`/`DefaultAiCurrentUserProvider` also exist, but nothing in this module
currently `@Autowired`s them -- every `AiQueryService` method takes the
`org.sers.webutils.model.security.User` as an explicit parameter instead. They're there as a
suggested shape for however *you* resolve "who is asking" (e.g. from your existing webutils
session/security context) in the controller/facade you write in the host app to call into
`AiQueryService`; declaring a bean for it is optional and has no effect unless your own code
looks it up.

### 3. For natural-language queries: `ai.api-key`

`AiChatClient`/`NaturalLanguageQueryTranslator` are registered by the same component scan, but
`AiChatClient.generate(...)` needs an API key at call time from whichever OpenAI-compatible
provider you point it at. Component-scanning this package also registers a
`PropertySourcesPlaceholderConfigurer` (`AiQueryModelConfig`) that resolves `${ai.*}`
placeholders from an `ai.local.properties` file **on the host application's own classpath**
(e.g. `src/main/resources/ai.local.properties` in the host project, gitignored, one per
developer/environment) -- this module's own jar never bundles one:

```properties
ai.api-key=sk-...
ai.base-url=https://dashscope-intl.aliyuncs.com/compatible-mode/v1
ai.model=qwen-plus
```

`ai.api-key`, `ai.base-url` and `ai.model` are all required -- there is no built-in default
provider, so a deployment that forgets to set one fails fast with a clear
`IllegalStateException` naming the missing property, rather than silently talking to some
other provider. `ai.vision-model` is required only if `generateWithImage(...)` is actually
called.

Any OpenAI-compatible provider works -- switching is a config-only change, no rebuild needed.
For example, to use Gemini instead:

```properties
ai.api-key=<gemini-api-key>
ai.base-url=https://generativelanguage.googleapis.com/v1beta/openai
ai.model=gemini-2.0-flash
```

If the host doesn't provide this file and doesn't configure `ai.api-key`/`ai.base-url`/`ai.model`
some other way, the Spring context still starts fine -- `AiChatClient` just throws a clear
`IllegalStateException` the first time `generate(...)` is actually called. Only
`queryFromNaturalLanguage(...)` needs this; `queryEntity(...)` and the rest of the structured
API never touch `AiChatClient`.

### 4. Optional: override query limits

`AiQueryProperties` ships with sensible defaults (page size, max filters, etc.) and is
picked up automatically by component-scanning. To override any value, declare your own
bean instead:

```xml

<bean class="org.pahappa.systems.aiquery.config.AiQueryProperties">
   <property name="defaultPageSize" value="25"/>
   <property name="maxPageSize" value="200"/>
</bean>
```

## Entry point

`service.org.pahappa.systems.aiquery.AiQueryService` is the class you call (`User` below is
`org.sers.webutils.model.security.User`):

- `listQueryableEntities(User)`
- `describeQueryableEntity(User, String entityName)`
- `queryEntity(User, String entityName, List<String> fields, List<FilterCriterion> filters, List<SortCriterion> sort, Integer page, Integer pageSize, AggregationRequest aggregation)`
- `queryFromNaturalLanguage(User, String naturalLanguageQuery)` -- translates the
  question via `AiChatClient`/`NaturalLanguageQueryTranslator`, then runs it through the exact
  same `queryEntity(...)` path above. The model only ever picks which already-authorized
  entity/field/operator/value to use; it cannot bypass validation.

Mark any JPA attribute that should never be exposed with `@annotation.org.pahappa.systems.aiquery.AiNotQueryable`.

A typical host-app controller method looks like:

```java
@RequestMapping(value = "/api/ai-query/ask", method = RequestMethod.POST)
@ResponseBody
public QueryResult ask(@RequestBody AskRequest request) {
    User user = resolveCurrentUser(); // e.g. from your existing webutils session/security context
    return aiQueryService.queryFromNaturalLanguage(user, request.getQuestion());
}
```

## Known risk / follow-up

This module is compiled and verified against the Java 8 language level and the
JPA 2.0/2.1 API surface (`javax.persistence`), but has **not** been run against Hibernate
3.5's actual Criteria/Metamodel implementation -- there was no such runtime available to
test against while porting it. Hibernate 3.5 was an early JPA 2.0 adopter and its
Criteria/Metamodel support has rough edges compared to modern Hibernate. Before relying on
this in `kpi-tracker-services`, smoke-test:

1. `AiEntityMetadataService.buildCatalog()` actually finds your `kpi-tracker-models`
   entities and their basic attributes via `entityManagerFactory.getMetamodel()`.
2. One `CriteriaQueryBuilder.executeSelect(...)` call against a real entity, including a
   filter, a sort, and pagination.
#   A i D a t a b a s e Q u e r y i n g  
 