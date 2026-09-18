# Architecture: ai-query-engine

A generic, reflection-driven, **read-only** natural-language query engine over JPA entities. It's a from-scratch reimplementation of `demo`'s Spring AI features, built to run under the legacy Java 8 / Spring 3.1 / `javax.persistence` stack the production apps use — no Spring Boot, no Spring AI anywhere.

## Request flow (`POST /ai-query/ask`)

```
AiController
  -> NaturalLanguageQueryTranslator   (NL question -> structured TranslatedQueryRequest, via LLM)
  -> AiQueryServiceImpl.queryEntity
      -> AiQueryValidator             (re-validates entity/fields/operators/values/limits + auth)
      -> CriteriaQueryBuilder         (JPA Criteria API execution, no SQL/JPQL strings)
      -> AiQueryAuditLogger           (structured audit log)
  -> QueryResultSynthesizer           (rows + question -> NL answer, via LLM)
  -> AskResponse (answer + raw QueryResult) as JSON
```

If the question doesn't map to any entity (`AiQueryValidationException`), the controller falls back to a plain advisory chat answer via `AiChatClient` instead of failing.

## Layers

- **web** — `AiController`: single Spring MVC `@Controller` endpoint, translates exceptions to HTTP status codes (403/400/500). `AiQueryObjectMapperFactory` builds the old Jackson-1.x-compatible mapper.
- **metadata** — `AiEntityMetadataService`: builds the entire queryable catalog once at startup (`@PostConstruct`) straight from the JPA `Metamodel` — no class-scanning. Filters out relationships beyond one level, `@AiNotQueryable`-annotated members, and anything credential-shaped by name (password/token/secret/…). This catalog is the single source of truth for what the AI can ever see.
- **service** — `NaturalLanguageQueryTranslator` (question -> JSON query spec, with a cheap entity-linking pre-pass when there are many accessible entities), `AiQueryServiceImpl` (orchestration), `QueryResultSynthesizer` (rows -> NL answer).
- **validation** — `AiQueryValidator`: the trust boundary. Every field/entity/operator/value the LLM names is independently re-resolved against the metadata catalog and re-authorized — the model can only ever select among what's already permitted, never bypass checks.
- **querybuilder** — `CriteriaQueryBuilder`: builds and executes strictly typed JPA `CriteriaQuery`s (one `LEFT JOIN` per relation), supports plain selects, aggregations (COUNT/SUM/AVG/MIN/MAX), percentage-of and duration aggregations (computed in Java where no portable SQL exists).
- **security** — `AiDatabaseAuthorizationService` interface with a permissive `DefaultAiDatabaseAuthorizationService`; host apps supply real ACL logic by wiring their own bean.
- **client** — `AiChatClient`: talks to any OpenAI-compatible chat/completions API (DashScope/Qwen, Gemini, etc.) via Apache HttpClient — provider is pure config (`ai.base-url`/`ai.api-key`/`ai.model`), with 429 retry handling.
- **audit** — `AiQueryAuditLogger`: logs who/what/how-many/how-long, deliberately never logs filter/sort *values*.
- **config** — `AiQueryProperties`/`AiQueryModelConfig`: resolves `ai.*` properties from an external file or `AI_PROPERTIES_FILE` env var, so secrets aren't shipped inside the WAR.

## Key design principles

- **Defense in depth**: LLM output is never trusted — every translated query is revalidated from scratch through the same path a direct API call would take.
- **Strictly read-only** by construction (no insert/update/delete code path anywhere).
- **No dynamic SQL/JPQL** — everything goes through typed JPA Criteria `Path`/`SingularAttribute` objects resolved from the metamodel.
- **Java 8 compatibility constraint** governs all code style (anonymous classes instead of lambdas where the codebase already does, no newer syntax).
