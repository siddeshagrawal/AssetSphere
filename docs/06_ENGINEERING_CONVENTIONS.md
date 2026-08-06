# AssetSphere Engineering Conventions

## Module and package rules

- Root package: `com.assetsphere`.
- Implement business capabilities as `modules.<module>`. A module owns its `api`, `domain`, `dto`, `mapper`, and persistence types; introduce only the packages that the module uses.
- `modules.common` contains stable, domain-neutral contracts: shared responses, errors, base persistence types, clocks, and small utilities.
- `infrastructure` contains adapters/configuration only. Business services depend on ports/interfaces, not MinIO, Redis, Kafka, or provider SDK classes.
- Modules communicate through public contracts or domain events; they do not reach into another module's repositories or entities.

## Java, class, DTO, mapper, and repository conventions

- Use Java 21 language features and Spring Boot-managed dependency versions unless a compatibility fix is documented.
- Use singular entity names, `XxxRepository`, `XxxService`, and `XxxServiceImpl` only where an interface is useful. Use `XxxMapper` for MapStruct interfaces.
- Request and response DTOs are Java records. Entities are never returned from controllers.
- Keep MapStruct mappers module-local; set `componentModel = "spring"` when a mapper is injected.
- Repositories express persistence queries only. Provider selection and security decisions do not belong in repositories.

## API, validation, exceptions, and logging

- Controllers are thin: validate a request, invoke one service use case, and return a shared response DTO.
- Use Jakarta validation on API requests. Validation failures use `VALIDATION_FAILED` with field violations.
- Throw typed business exceptions (`ResourceNotFoundException`, `BusinessRuleViolationException`, and module-specific exceptions) and map them centrally in `GlobalExceptionHandler`.
- Never expose stack traces, provider exception messages, tokens, or secrets to clients.
- Log structured context using SLF4J placeholders: `workspaceId`, `assetId`, `eventId`, and the correlation ID. Do not log file contents, access tokens, API keys, or credentials.

## Entity and transaction conventions

- Extend `BaseEntity` for mutable JPA aggregate roots. UUID IDs, UTC timestamps, auditing fields, and `@Version` are standard.
- Keep immutable audit rows and asset versions as module-owned append-only entities; do not use generic mutation helpers.
- Place `@Transactional` on application service use cases. Read use cases may use `@Transactional(readOnly = true)`; event/outbox state transitions use explicit transactions.
- Use Flyway for schema changes. `ddl-auto` remains `validate` outside tests.

## Events, Kafka, outbox, retry, and DLQ

- Event types use a stable past-tense convention, for example `asset.uploaded.v1` and `workspace.created.v1`.
- Kafka topics use `assetsphere.<module>.<event>.<version>`; consumer groups use `assetsphere.<module>.<purpose>`.
- Every externally published business event is persisted to the Processing-owned outbox in the same transaction as its source change.
- Outbox rows require event ID, aggregate type/ID, event type/version, JSON payload, status, attempt count, next-attempt time, error summary, and published time.
- The outbox publisher is scheduled, idempotent, and publishes with an aggregate-based key. Retry uses bounded exponential backoff; exhausted events move to a durable dead-letter state for operator review.
- Do not publish Kafka messages directly from a transaction that changes business state.

## Redis, caching, idempotency, rate limiting, and locking

- Redis keys are colon-separated and namespaced: `assetsphere:<concern>:<scope>:<id>`.
- Cache names use lower-kebab-case, for example `workspace-settings` and `latest-version`. Cache values are DTOs, never live entities.
- Prefer explicit cache/idempotency/lock ports when behavior is cross-cutting or requires failure policy. Use caching annotations only for simple, local read caches with explicit eviction.
- Write endpoints that need replay safety require an `Idempotency-Key`; keys are scoped to authenticated actor, workspace when applicable, HTTP method, and route. Store `IN_PROGRESS` atomically, replay successful responses, and clear failed claims.
- Redis unavailability policy is use-case-specific: compliance-critical mutations fail closed; optional optimizations may fail open and must be observable.
- Distributed locks are used only for documented cross-node critical sections such as Asset version promotion. Keys use `assetsphere:lock:<resource>:<id>`, have bounded leases, and are always released in `finally`.
- Rate limiter implementations are ports selected by configuration. Start with a Redis atomic sliding-window or token-bucket implementation when Auth defines user/API-key scopes; return standard 429 headers.

## Security and provider-extension rules

- Security is stateless. JWT authentication and workspace authorization are implemented together with Auth and Workspace.
- Apply `@EnableMethodSecurity` and module-local authorization expressions only when user/workspace context exists. Controllers do not hand-roll authorization.
- API keys are limited to explicitly documented machine-to-machine endpoints.
- Storage, intelligence, notification, and future payment providers are interface-backed adapters. Add a provider by adding an implementation/configuration, not a provider conditional in a business service.
- Dev profiles use local Docker dependencies; production uses required environment variables and managed equivalents. No secret is committed to source, docs, or examples.

## Testing and Modulith rules

- Test classes use `XxxTests` and method names describe behavior, for example `createsVersionWhenContentChanges`.
- Unit-test state transitions, authorization decisions, provider adapters, idempotency behavior, and retry calculations. Use integration tests for Flyway, repositories, and Kafka/Redis boundaries when their module exists.
- Keep an `ApplicationModules.verify()` architecture test. Add explicit module declarations and named interfaces only when a module has a real public contract.
